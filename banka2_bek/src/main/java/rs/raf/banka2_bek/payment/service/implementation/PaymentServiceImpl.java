package rs.raf.banka2_bek.payment.service.implementation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.CalculateExchangeResponseDto;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentDirection;
import rs.raf.banka2_bek.payment.dto.PaymentListItemDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.payment.repository.PaymentAccountRepository;
import rs.raf.banka2_bek.payment.repository.PaymentRepository;
import rs.raf.banka2_bek.payment.service.PaymentReceiptPdfGenerator;
import rs.raf.banka2_bek.payment.service.PaymentService;
import rs.raf.banka2_bek.notification.service.MailNotificationService;
import rs.raf.banka2_bek.transaction.dto.TransactionListItemDto;
import rs.raf.banka2_bek.transaction.dto.TransactionResponseDto;
import rs.raf.banka2_bek.transaction.dto.TransactionType;
import rs.raf.banka2_bek.transaction.service.TransactionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@lombok.extern.slf4j.Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAccountRepository paymentAccountRepository;
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final TransactionService transactionService;
    private final PaymentReceiptPdfGenerator paymentReceiptPdfGenerator;
    private final ExchangeService exchangeService;
    private final MailNotificationService mailNotificationService;
    private final String bankRegistrationNumber;
    private static final int ORDER_NUMBER_MAX_RETRIES = 5;
    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.005"); // 0.5%

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              PaymentAccountRepository paymentAccountRepository,
                              AccountRepository accountRepository,
                              ClientRepository clientRepository,
                              TransactionService transactionService,
                              PaymentReceiptPdfGenerator paymentReceiptPdfGenerator,
                              ExchangeService exchangeService,
                              MailNotificationService mailNotificationService,
                              @Value("${bank.registration-number}") String bankRegistrationNumber) {
        this.paymentRepository = paymentRepository;
        this.paymentAccountRepository = paymentAccountRepository;
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.transactionService = transactionService;
        this.paymentReceiptPdfGenerator = paymentReceiptPdfGenerator;
        this.exchangeService = exchangeService;
        this.mailNotificationService = mailNotificationService;
        this.bankRegistrationNumber = bankRegistrationNumber;
    }

    /**
     * PAYMENT FLOW (od nule):
     *
     * 1. Validacija (racuni, vlasnistvo, status)
     * 2. Izracunaj proviziju (0.5% za cross-currency, 0 za same-currency)
     * 3. Proveri da klijent ima AMOUNT + PROVIZIJA na racunu
     * 4. Proveri dnevni/mesecni limit
     * 5. Za cross-currency: konvertuj preko banke
     * 6. Skini AMOUNT + PROVIZIJA od klijenta (jedan poziv, jedan iznos)
     * 7. Dodaj AMOUNT primaocu
     * 8. Dodaj AMOUNT + PROVIZIJA banci (za cross-currency) ili samo PROVIZIJU (za same-currency)
     * 9. Sacuvaj payment, kreiraj transakcije, posalji email
     */
    @Override
    @Transactional
    public PaymentResponseDto createPayment(CreatePaymentRequestDto request) {
        // ===== 1. VALIDACIJA =====
        Account fromAccount = paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())
                .orElseThrow(() -> new IllegalArgumentException("Racun posiljaoca ne postoji."));

        Account toAccount = paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())
                .orElseThrow(() -> new IllegalArgumentException("Racun primaoca ne postoji."));

        if (fromAccount.getStatus() != AccountStatus.ACTIVE)
            throw new IllegalArgumentException("Racun posiljaoca nije aktivan.");
        if (toAccount.getStatus() != AccountStatus.ACTIVE)
            throw new IllegalArgumentException("Racun primaoca nije aktivan.");
        if (fromAccount.getId().equals(toAccount.getId()))
            throw new IllegalArgumentException("Racuni moraju biti razliciti.");

        Client client = getAuthenticatedClient();
        if (fromAccount.getClient() == null || !fromAccount.getClient().getId().equals(client.getId()))
            throw new IllegalArgumentException("Racun ne pripada klijentu.");

        // ===== 2. PROVIZIJA =====
        BigDecimal amount = request.getAmount();
        boolean isCrossCurrency = !fromAccount.getCurrency().getId().equals(toAccount.getCurrency().getId());
        BigDecimal fee = isCrossCurrency
                ? amount.multiply(COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal totalFromClient = amount.add(fee); // ovo se skida od klijenta

        // ===== 3. PROVERA SREDSTAVA =====
        if (fromAccount.getAvailableBalance().compareTo(totalFromClient) < 0)
            throw new IllegalArgumentException("Nedovoljno sredstava na racunu. Potrebno: " + totalFromClient);

        // ===== 4. LIMITI =====
        if (fromAccount.getDailyLimit() != null && fromAccount.getDailyLimit().compareTo(BigDecimal.ZERO) > 0
                && fromAccount.getDailySpending().add(amount).compareTo(fromAccount.getDailyLimit()) > 0)
            throw new IllegalArgumentException("Prekoracen dnevni limit.");
        if (fromAccount.getMonthlyLimit() != null && fromAccount.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0
                && fromAccount.getMonthlySpending().add(amount).compareTo(fromAccount.getMonthlyLimit()) > 0)
            throw new IllegalArgumentException("Prekoracen mesecni limit.");

        // ===== 5. CROSS-CURRENCY KONVERZIJA =====
        BigDecimal creditedAmount = amount; // koliko primalac dobija
        if (isCrossCurrency) {
            String fromCurr = fromAccount.getCurrency().getCode();
            String toCurr = toAccount.getCurrency().getCode();

            CalculateExchangeResponseDto fx = exchangeService.calculateCross(
                    amount.doubleValue(), fromCurr, toCurr);
            creditedAmount = BigDecimal.valueOf(fx.getConvertedAmount());

            // Bankin racun za target valutu mora imati dovoljno
            Account bankToAccount = accountRepository.findBankAccountForUpdateByCurrency(bankRegistrationNumber, toCurr)
                    .orElseThrow(() -> new RuntimeException("Banka nema racun za " + toCurr));
            if (bankToAccount.getAvailableBalance().compareTo(creditedAmount) < 0)
                throw new RuntimeException("Banka nema dovoljno " + toCurr);

            // Banka placa target valutu primaocu
            bankToAccount.setBalance(bankToAccount.getBalance().subtract(creditedAmount));
            bankToAccount.setAvailableBalance(bankToAccount.getAvailableBalance().subtract(creditedAmount));
            accountRepository.save(bankToAccount);

            // Banka prima source valutu (amount + provizija) od klijenta
            Account bankFromAccount = accountRepository.findBankAccountForUpdateByCurrency(bankRegistrationNumber, fromCurr)
                    .orElseThrow(() -> new RuntimeException("Banka nema racun za " + fromCurr));
            bankFromAccount.setBalance(bankFromAccount.getBalance().add(totalFromClient));
            bankFromAccount.setAvailableBalance(bankFromAccount.getAvailableBalance().add(totalFromClient));
            accountRepository.save(bankFromAccount);
        }

        // ===== 6. SKINI OD KLIJENTA (amount + fee, JEDNOM) =====
        fromAccount.setBalance(fromAccount.getBalance().subtract(totalFromClient));
        fromAccount.setAvailableBalance(fromAccount.getAvailableBalance().subtract(totalFromClient));
        fromAccount.setDailySpending(fromAccount.getDailySpending().add(amount));
        fromAccount.setMonthlySpending(fromAccount.getMonthlySpending().add(amount));

        // ===== 7. DODAJ PRIMAOCU =====
        toAccount.setBalance(toAccount.getBalance().add(creditedAmount));
        toAccount.setAvailableBalance(toAccount.getAvailableBalance().add(creditedAmount));

        // ===== 8. SACUVAJ PAYMENT =====
        String paymentCode = request.getPaymentCode().getCode();
        Payment payment = Payment.builder()
                .fromAccount(fromAccount)
                .toAccountNumber(request.getToAccount())
                .amount(amount)
                .fee(fee)
                .currency(fromAccount.getCurrency())
                .recipientName(request.getRecipientName())
                .paymentCode(paymentCode)
                .referenceNumber(request.getReferenceNumber())
                .purpose(request.getDescription())
                .status(PaymentStatus.COMPLETED)
                .createdBy(client)
                .build();

        Payment savedPayment = null;
        for (int attempt = 1; attempt <= ORDER_NUMBER_MAX_RETRIES; attempt++) {
            try {
                payment.setOrderNumber(generateOrderNumber());
                savedPayment = paymentRepository.saveAndFlush(payment);
                break;
            } catch (DataIntegrityViolationException ex) {
                String msg = ex.getMostSpecificCause().getMessage();
                if (msg == null) throw ex;
                String lower = msg.toLowerCase();
                if (!(lower.contains("order_number") || lower.contains("uk") || lower.contains("unique")))
                    throw ex;
            }
        }
        if (savedPayment == null)
            throw new IllegalStateException("Generisanje broja placanja nije uspelo.");

        // ===== 9. TRANSAKCIJE + EMAIL =====
        transactionService.recordPaymentSettlement(savedPayment, toAccount, client, creditedAmount);

        try {
            mailNotificationService.sendPaymentConfirmationMail(
                    client.getEmail(), amount,
                    fromAccount.getCurrency() != null ? fromAccount.getCurrency().getCode() : null,
                    fromAccount.getAccountNumber(), request.getToAccount(),
                    savedPayment.getCreatedAt() != null ? savedPayment.getCreatedAt().toLocalDate() : java.time.LocalDate.now(),
                    "COMPLETED");
        } catch (Exception e) {
            log.warn("Failed to send payment confirmation email: {}", e.getMessage());
        }

        return toResponse(savedPayment, client.getId());
    }

    @Override
    public Page<PaymentListItemDto> getPayments(Pageable pageable, LocalDateTime fromDate, LocalDateTime toDate,
            String accountNumber, BigDecimal minAmount, BigDecimal maxAmount, PaymentStatus status) {
        Client client = getOptionalClient();
        if (client == null) return Page.empty(pageable);
        return paymentRepository.findByUserAccountsWithFilters(client.getId(), fromDate, toDate, accountNumber, minAmount, maxAmount, status, pageable)
                .map(p -> toListItem(p, client.getId()));
    }

    @Override
    public PaymentResponseDto getPaymentById(Long paymentId) {
        Client client = getAuthenticatedClient();
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Placanje nije pronadjeno."));
        return toResponse(payment, client.getId());
    }

    @Override
    public byte[] getPaymentReceipt(Long paymentId) {
        Long clientId = getAuthenticatedClient().getId();
        TransactionResponseDto transaction = transactionService.getReceiptTransaction(paymentId, clientId);
        return paymentReceiptPdfGenerator.generate(transaction);
    }

    @Override
    public Page<TransactionListItemDto> getPaymentHistory(Pageable pageable, LocalDateTime fromDate, LocalDateTime toDate,
            BigDecimal minAmount, BigDecimal maxAmount, TransactionType type) {
        return transactionService.getTransactions(pageable, fromDate, toDate, minAmount, maxAmount, type);
    }

    // ===== MAPPERS =====

    private PaymentResponseDto toResponse(Payment p, Long clientId) {
        return PaymentResponseDto.builder()
                .id(p.getId()).orderNumber(p.getOrderNumber())
                .fromAccount(p.getFromAccount() != null ? p.getFromAccount().getAccountNumber() : null)
                .toAccount(p.getToAccountNumber()).amount(p.getAmount()).fee(p.getFee())
                .currency(p.getCurrency() != null ? p.getCurrency().getCode() : null)
                .paymentCode(p.getPaymentCode()).referenceNumber(p.getReferenceNumber())
                .description(p.getPurpose()).recipientName(p.getRecipientName())
                .direction(resolveDirection(p, clientId)).status(p.getStatus()).createdAt(p.getCreatedAt())
                .build();
    }

    private PaymentListItemDto toListItem(Payment p, Long clientId) {
        return PaymentListItemDto.builder()
                .id(p.getId()).orderNumber(p.getOrderNumber())
                .fromAccount(p.getFromAccount() != null ? p.getFromAccount().getAccountNumber() : null)
                .toAccount(p.getToAccountNumber()).amount(p.getAmount()).fee(p.getFee())
                .currency(p.getCurrency() != null ? p.getCurrency().getCode() : null)
                .recipientName(p.getRecipientName()).description(p.getPurpose())
                .direction(resolveDirection(p, clientId)).status(p.getStatus()).createdAt(p.getCreatedAt())
                .build();
    }

    private PaymentDirection resolveDirection(Payment p, Long clientId) {
        if (p.getFromAccount() == null || p.getFromAccount().getClient() == null) return PaymentDirection.INCOMING;
        return p.getFromAccount().getClient().getId().equals(clientId) ? PaymentDirection.OUTGOING : PaymentDirection.INCOMING;
    }

    // ===== AUTH HELPERS =====

    private String getAuthenticatedUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new IllegalArgumentException("Niste prijavljeni.");
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails ud) return ud.getUsername();
        throw new IllegalArgumentException("Niste prijavljeni.");
    }

    private Client getAuthenticatedClient() {
        Client c = getOptionalClient();
        if (c == null) throw new IllegalArgumentException("Klijent nije pronadjen.");
        return c;
    }

    private Client getOptionalClient() {
        try { return clientRepository.findByEmail(getAuthenticatedUsername()).orElse(null); }
        catch (Exception e) {
            log.warn("Failed to resolve client: {}", e.getMessage());
            return null;
        }
    }

    private String generateOrderNumber() {
        return "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
