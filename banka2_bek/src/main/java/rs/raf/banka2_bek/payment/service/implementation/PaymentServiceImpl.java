package rs.raf.banka2_bek.payment.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.repository.AccountRepository;
import rs.raf.banka2_bek.payment.repository.PaymentRepository;
import rs.raf.banka2_bek.payment.service.PaymentService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private static final int ORDER_NUMBER_MAX_RETRIES = 5;

    @Override
    public PaymentResponseDto createPayment(CreatePaymentRequestDto request) {
        Account fromAccount = accountRepository.findByAccountNumber(request.getFromAccount())
                .orElseThrow(() -> new IllegalArgumentException("Source account does not exist."));

        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Source account is not active.");
        }

        User client = getAuthenticatedClient();

        if (fromAccount.getUser() == null || !fromAccount.getUser().getId().equals(client.getId())) {
            throw new IllegalArgumentException("Source account does not belong to the authenticated client.");
        }

        if (fromAccount.getDailyLimit() == null
                || fromAccount.getDailySpending().add(request.getAmount()).compareTo(fromAccount.getDailyLimit()) > 0) {
            throw new IllegalArgumentException("Daily transfer limit exceeded for the source account.");
        }

        if (fromAccount.getMonthlyLimit() == null
                || fromAccount.getMonthlySpending().add(request.getAmount()).compareTo(fromAccount.getMonthlyLimit()) > 0) {
            throw new IllegalArgumentException("Monthly transfer limit exceeded for the source account.");
        }

        if (fromAccount.getAvailableBalance() == null || fromAccount.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            throw new IllegalArgumentException("Insufficient funds in the source account.");
        }
        //TODO: proveriti da ne salje slucajno sam sebi??
        Payment base = Payment.builder()
//                .orderNumber(generateUniqueOrderNumber())
                .fromAccount(fromAccount)
                .toAccountNumber(request.getToAccount())
                .amount(request.getAmount())
                .currency(fromAccount.getCurrency())
                .paymentCode(request.getPaymentCode())
                .referenceNumber(request.getReferenceNumber())
                .purpose(request.getDescription())
                .createdBy(client)
                .build();

        for (int attempt = 1; attempt <= ORDER_NUMBER_MAX_RETRIES; attempt++) {
            try {
                base.setOrderNumber(generateOrderNumber());
                return toResponse(paymentRepository.saveAndFlush(base)); // force DB unique check now
            } catch (DataIntegrityViolationException ex) {
                String msg = ex.getMostSpecificCause().getMessage();
                if (msg == null) throw ex;

                String lower = msg.toLowerCase();
                if (!(lower.contains("order_number") || lower.contains("uk") || lower.contains("unique")))
                    throw ex;
            }
        }
        throw new IllegalStateException("Failed to generate unique order number.");
    }

    @Override
    public List<PaymentResponseDto> getPayments() {
        return paymentRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public PaymentResponseDto getPaymentById(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment with ID " + paymentId + " not found."));
        return toResponse(payment);
    }

    @Override
    public List<PaymentResponseDto> getPaymentHistory() {
        // For now history returns the same list as /payments.
        return getPayments();
    }

    private PaymentResponseDto toResponse(Payment payment) {
        return PaymentResponseDto.builder()
                .id(payment.getId())
                .orderNumber(payment.getOrderNumber())
                .fromAccount(payment.getFromAccount() != null ? payment.getFromAccount().getAccountNumber() : null)
                .toAccount(payment.getToAccountNumber())
                .amount(payment.getAmount())
                .paymentCode(payment.getPaymentCode())
                .referenceNumber(payment.getReferenceNumber())
                .description(payment.getPurpose())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        throw new IllegalArgumentException("Authenticated user is required.");
    }

    private User getAuthenticatedClient() {
        String username = getAuthenticatedUsername();
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated client does not exist."));
    }

    private String generateOrderNumber() {
        return "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}

