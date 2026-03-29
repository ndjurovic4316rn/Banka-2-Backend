package rs.raf.banka2_bek.margin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.margin.dto.CreateMarginAccountDto;
import rs.raf.banka2_bek.margin.dto.MarginAccountDto;
import rs.raf.banka2_bek.margin.dto.MarginTransactionDto;
import rs.raf.banka2_bek.margin.model.MarginAccount;
import rs.raf.banka2_bek.margin.model.MarginAccountStatus;
import rs.raf.banka2_bek.margin.model.MarginTransaction;
import rs.raf.banka2_bek.margin.model.MarginTransactionType;
import rs.raf.banka2_bek.margin.repository.MarginAccountRepository;
import rs.raf.banka2_bek.margin.repository.MarginTransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Servis za upravljanje margin racunima.
 *
 * Specifikacija: Celina 3 - Margin racuni
 *
 * Kljucne formule:
 *   initialMargin     = deposit / (1 - bankParticipation)
 *   loanValue          = initialMargin - deposit
 *   maintenanceMargin  = initialMargin * 0.5  (za akcije)
 *
 * Margin call: ako initialMargin padne ispod maintenanceMargin, racun se blokira.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarginAccountService {

    private final MarginAccountRepository marginAccountRepository;
    private final MarginTransactionRepository marginTransactionRepository;
    private final AccountRepository accountRepository;

    /** Podrazumevani procenat ucestva banke (50%) */
    private static final BigDecimal DEFAULT_BANK_PARTICIPATION = new BigDecimal("0.50");

    /** Faktor za izracunavanje maintenance margine (50% od initial za akcije) */
    private static final BigDecimal MAINTENANCE_FACTOR = new BigDecimal("0.50");

    /**
     * Kreira novi margin racun za korisnika.
     *
     * @param userId ID korisnika koji kreira margin racun
     * @param dto DTO sa accountId i initialDeposit
     * @return kreiran MarginAccountDto
     */
    @Transactional
    public MarginAccountDto createForUser(Long userId, CreateMarginAccountDto dto) {
        if (userId == null) {
            throw new IllegalArgumentException("Authenticated user id is required.");
        }
        if (dto == null || dto.getAccountId() == null || dto.getInitialDeposit() == null) {
            throw new IllegalArgumentException("Account id and initial deposit are required.");
        }

        BigDecimal initialDeposit = dto.getInitialDeposit();
        if (initialDeposit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Initial deposit must be greater than zero.");
        }

        var account = accountRepository.findForUpdateById(dto.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));

        if (account.getClient() == null || !userId.equals(account.getClient().getId())) {
            throw new IllegalStateException("You are not allowed to create a margin account for this base account.");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Base account must be active.");
        }
        if (!marginAccountRepository.findByAccountId(account.getId()).isEmpty()) {
            throw new IllegalArgumentException("Margin account already exists for this base account.");
        }

        BigDecimal availableBalance = account.getAvailableBalance() == null
                ? BigDecimal.ZERO
                : account.getAvailableBalance();
        if (availableBalance.compareTo(initialDeposit) < 0) {
            throw new IllegalArgumentException("Insufficient available balance for initial margin deposit.");
        }

        BigDecimal divisor = BigDecimal.ONE.subtract(DEFAULT_BANK_PARTICIPATION);
        BigDecimal initialMargin = initialDeposit.divide(divisor, 4, RoundingMode.HALF_UP);
        BigDecimal loanValue = initialMargin.subtract(initialDeposit).setScale(4, RoundingMode.HALF_UP);
        BigDecimal maintenanceMargin = initialMargin.multiply(MAINTENANCE_FACTOR).setScale(4, RoundingMode.HALF_UP);

        account.setAvailableBalance(availableBalance.subtract(initialDeposit));
        if (account.getBalance() != null) {
            account.setBalance(account.getBalance().subtract(initialDeposit));
        }

        MarginAccount savedMarginAccount = marginAccountRepository.save(
                MarginAccount.builder()
                        .account(account)
                        .userId(userId)
                        .initialMargin(initialMargin)
                        .loanValue(loanValue)
                        .maintenanceMargin(maintenanceMargin)
                        .bankParticipation(DEFAULT_BANK_PARTICIPATION)
                        .status(MarginAccountStatus.ACTIVE)
                        .build()
        );

        marginTransactionRepository.save(
                MarginTransaction.builder()
                        .marginAccount(savedMarginAccount)
                        .type(MarginTransactionType.DEPOSIT)
                        .amount(initialDeposit.setScale(4, RoundingMode.HALF_UP))
                        .description("Initial margin deposit")
                        .build()
        );

        log.info("Created margin account {} for user {} on base account {}",
                savedMarginAccount.getId(), userId, account.getId());

        return toDto(savedMarginAccount);
    }

    /**
     * Vraca sve margin racune za autentifikovanog korisnika.
     *
     * TODO: Implementirati logiku:
     *   1. Pronaci korisnika po email-u (User ili Employee)
     *   2. Dohvatiti sve margin racune za tog korisnika (findByUserId)
     *   3. Mapirati u listu MarginAccountDto
     *
     * @param email email autentifikovanog korisnika
     * @return lista margin racuna
     */
    public List<MarginAccountDto> getMyMarginAccounts(String email) {
        // TODO: Look up user by email and fetch their margin accounts
        log.info("Fetching margin accounts for user {}", email);
        return List.of();
    }

    /**
     * Uplata sredstava na margin racun.
     *
     * TODO: Implementirati logiku:
     *   1. Pronaci MarginAccount po ID-ju, baciti exception ako ne postoji
     *   2. Dodati amount na initialMargin
     *   3. Preracunati maintenanceMargin = initialMargin * MAINTENANCE_FACTOR
     *   4. Ako je racun bio BLOCKED i sada initialMargin >= maintenanceMargin:
     *      - Promeniti status na ACTIVE
     *      - Logirati "Margin account {} unblocked after deposit of {}"
     *   5. Sacuvati MarginAccount
     *   6. Kreirati DEPOSIT MarginTransaction
     *   7. Sacuvati MarginTransaction
     *
     * @param marginAccountId ID margin racuna
     * @param amount iznos za uplatu
     */
    @Transactional
    public void deposit(Long marginAccountId, BigDecimal amount) {
        // TODO: Implement deposit logic with margin recalculation
        log.info("Deposit {} to margin account {}", amount, marginAccountId);
    }

    /**
     * Isplata sredstava sa margin racuna.
     *
     * TODO: Implementirati logiku:
     *   1. Pronaci MarginAccount po ID-ju, baciti exception ako ne postoji
     *   2. Proveriti da je racun ACTIVE (blokirani racuni ne dozvoljavaju isplate)
     *   3. Proveriti: initialMargin - amount >= maintenanceMargin
     *      - Ako nije, baciti exception "Isplata bi smanjila marginu ispod maintenance nivoa"
     *   4. Smanjiti initialMargin za amount
     *   5. Sacuvati MarginAccount
     *   6. Kreirati WITHDRAWAL MarginTransaction
     *   7. Sacuvati MarginTransaction
     *
     * @param marginAccountId ID margin racuna
     * @param amount iznos za isplatu
     */
    @Transactional
    public void withdraw(Long marginAccountId, BigDecimal amount) {
        // TODO: Implement withdrawal logic with maintenance margin check
        log.info("Withdraw {} from margin account {}", amount, marginAccountId);
    }

    /**
     * Dnevna provera maintenance margine za sve aktivne margin racune.
     * Pokrece se automatski svaki dan u ponoc.
     *
     * TODO: Implementirati logiku:
     *   1. Dohvatiti sve margin racune sa statusom ACTIVE
     *   2. Za svaki racun proveriti: da li je initialMargin < maintenanceMargin?
     *   3. Ako jeste — margin call:
     *      - Postaviti status na BLOCKED
     *      - Sacuvati racun
     *      - Logirati "MARGIN CALL: Account {} blocked. initialMargin={}, maintenanceMargin={}"
     *   4. Na kraju logirati ukupan broj blokiranih racuna
     *   5. TODO (buducnost): Poslati email notifikaciju korisniku o margin call-u
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void checkMaintenanceMargin() {
        // TODO: Implement margin call logic for all active accounts
        log.info("Running daily maintenance margin check...");
    }

    /**
     * Vraca istoriju transakcija za dati margin racun.
     *
     * TODO: Implementirati logiku:
     *   1. Proveriti da margin racun postoji
     *   2. Dohvatiti sve transakcije (findByMarginAccountIdOrderByCreatedAtDesc)
     *   3. Mapirati u listu MarginTransactionDto
     *
     * @param marginAccountId ID margin racuna
     * @return lista transakcija sortirana od najnovije
     */
    public List<MarginTransactionDto> getTransactions(Long marginAccountId) {
        // TODO: Validate account access before returning transactions
        return marginTransactionRepository.findByMarginAccountIdOrderByCreatedAtDesc(marginAccountId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ── Helper metode ───────────────────────────────────────────────────────────

    /**
     * TODO: Mapira MarginAccount entitet u MarginAccountDto.
     */
    private MarginAccountDto toDto(MarginAccount marginAccount) {
        return MarginAccountDto.builder()
                .id(marginAccount.getId())
                .accountId(marginAccount.getAccount() != null ? marginAccount.getAccount().getId() : null)
                .accountNumber(marginAccount.getAccount() != null ? marginAccount.getAccount().getAccountNumber() : null)
                .userId(marginAccount.getUserId())
                .initialMargin(marginAccount.getInitialMargin())
                .loanValue(marginAccount.getLoanValue())
                .maintenanceMargin(marginAccount.getMaintenanceMargin())
                .bankParticipation(marginAccount.getBankParticipation())
                .status(marginAccount.getStatus() != null ? marginAccount.getStatus().name() : null)
                .createdAt(marginAccount.getCreatedAt())
                .build();
    }

    private MarginTransactionDto toDto(MarginTransaction transaction) {
        return MarginTransactionDto.builder()
                .id(transaction.getId())
                .marginAccountId(transaction.getMarginAccount() != null ? transaction.getMarginAccount().getId() : null)
                .type(transaction.getType() != null ? transaction.getType().name() : null)
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
