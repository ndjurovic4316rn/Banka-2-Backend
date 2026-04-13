package rs.raf.banka2_bek.order.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.order.exception.InsufficientFundsException;
import rs.raf.banka2_bek.order.exception.InsufficientHoldingsException;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Jedina klasa odgovorna za manipulaciju {@link Account#getAvailableBalance()},
 * {@link Account#getReservedAmount()} i {@link Portfolio#getReservedQuantity()}.
 *
 * Sve public metode su {@link Transactional} i koriste pessimistic write lock
 * pri ponovnom ucitavanju entiteta kako bi se zastitile od race-a sa schedulerom.
 */
@Service
@RequiredArgsConstructor
public class FundReservationService {

    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;

    // ── BUY ──────────────────────────────────────────────────────────────────

    /**
     * Rezervise {@code order.reservedAmount} na racunu. Smanjuje
     * {@code availableBalance}, povecava {@code reservedAmount}; ukupan balance
     * se ne menja.
     */
    @Transactional
    public void reserveForBuy(Order order, Account account) {
        if (order.isReservationReleased()) {
            throw new IllegalStateException(
                    "Rezervacija je vec oslobodjena za order " + order.getId());
        }
        BigDecimal amount = order.getReservedAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Iznos rezervacije mora biti pozitivan");
        }

        // Ponovno ucitaj pod lockom ako je dostupno (kad je id setovan).
        Account locked = account;
        if (account.getId() != null) {
            locked = accountRepository.findForUpdateById(account.getId())
                    .orElse(account);
        }

        if (locked.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Nedovoljno raspolozivih sredstava na racunu " + locked.getAccountNumber());
        }

        locked.setAvailableBalance(locked.getAvailableBalance().subtract(amount));
        locked.setReservedAmount(locked.getReservedAmount().add(amount));

        // Drzimo prosledjenu instancu u sinhronizaciji (kad su razliciti objekti).
        if (locked != account) {
            account.setAvailableBalance(locked.getAvailableBalance());
            account.setReservedAmount(locked.getReservedAmount());
        }
        accountRepository.save(locked);
    }

    /**
     * Oslobadja preostalu rezervaciju na racunu. Idempotentno — ako je
     * rezervacija vec oslobodjena, vraca se bez ikakvih izmena.
     */
    @Transactional
    public void releaseForBuy(Order order) {
        if (order.isReservationReleased()) {
            return;
        }
        if (order.getReservedAccountId() == null || order.getReservedAmount() == null) {
            order.setReservationReleased(true);
            return;
        }

        Account account = accountRepository.findForUpdateById(order.getReservedAccountId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Racun za rezervaciju ne postoji: " + order.getReservedAccountId()));

        BigDecimal remaining = currentReservedOnOrder(account, order);

        account.setAvailableBalance(account.getAvailableBalance().add(remaining));
        account.setReservedAmount(account.getReservedAmount().subtract(remaining));
        accountRepository.save(account);

        order.setReservationReleased(true);
    }

    /**
     * Knjizi jedan partial fill. Smanjuje {@code balance} za realnu cenu fill-a
     * i smanjuje {@code reservedAmount} proporcionalno ({@code qty / order.quantity}
     * deo originalne rezervacije).
     */
    @Transactional
    public void consumeForBuyFill(Order order, int qty, BigDecimal fillPrice) {
        if (order.isReservationReleased()) {
            throw new IllegalStateException(
                    "Rezervacija je vec oslobodjena za order " + order.getId());
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("Kolicina mora biti pozitivna");
        }

        Account account = accountRepository.findForUpdateById(order.getReservedAccountId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Racun za rezervaciju ne postoji: " + order.getReservedAccountId()));

        BigDecimal reservedPortion = new BigDecimal(qty)
                .divide(new BigDecimal(order.getQuantity()), 10, RoundingMode.HALF_UP)
                .multiply(order.getReservedAmount())
                .setScale(4, RoundingMode.HALF_UP);

        // Ne smemo dozvoliti da reservedAmount ode ispod nule zbog zaokruzivanja.
        BigDecimal currentReserved = account.getReservedAmount();
        if (reservedPortion.compareTo(currentReserved) > 0) {
            reservedPortion = currentReserved;
        }

        account.setBalance(account.getBalance().subtract(fillPrice));
        account.setReservedAmount(currentReserved.subtract(reservedPortion));
        accountRepository.save(account);
    }

    // ── SELL ─────────────────────────────────────────────────────────────────

    /**
     * Rezervise kolicinu hartija za SELL order.
     * Povecava {@code portfolio.reservedQuantity} za {@code order.quantity}.
     */
    @Transactional
    public void reserveForSell(Order order, Portfolio portfolio) {
        if (order.isReservationReleased()) {
            throw new IllegalStateException(
                    "Rezervacija je vec oslobodjena za order " + order.getId());
        }
        int need = order.getQuantity();
        if (need <= 0) {
            throw new IllegalArgumentException("Kolicina mora biti pozitivna");
        }
        if (portfolio.getAvailableQuantity() < need) {
            throw new InsufficientHoldingsException(
                    "Nedovoljno hartija: dostupno " + portfolio.getAvailableQuantity()
                            + ", traženo " + need);
        }
        portfolio.setReservedQuantity(portfolio.getReservedQuantity() + need);
        portfolioRepository.save(portfolio);
    }

    /**
     * Oslobadja rezervisanu kolicinu hartija. Idempotentno.
     */
    @Transactional
    public void releaseForSell(Order order, Portfolio portfolio) {
        if (order.isReservationReleased()) {
            return;
        }
        int toRelease = order.getRemainingPortions() != null
                ? order.getRemainingPortions()
                : order.getQuantity();
        int newReserved = Math.max(0, portfolio.getReservedQuantity() - toRelease);
        portfolio.setReservedQuantity(newReserved);
        portfolioRepository.save(portfolio);
        order.setReservationReleased(true);
    }

    /**
     * Knjizi partial fill za SELL order. Smanjuje i ukupnu kolicinu u portfoliu
     * i rezervisanu kolicinu za {@code qty}.
     */
    @Transactional
    public void consumeForSellFill(Order order, Portfolio portfolio, int qty) {
        if (order.isReservationReleased()) {
            throw new IllegalStateException(
                    "Rezervacija je vec oslobodjena za order " + order.getId());
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("Kolicina mora biti pozitivna");
        }
        portfolio.setQuantity(portfolio.getQuantity() - qty);
        portfolio.setReservedQuantity(Math.max(0, portfolio.getReservedQuantity() - qty));
        portfolioRepository.save(portfolio);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Procena koliko je od originalne rezervacije ordera jos uvek "zakacilo"
     * na account-u. Racunamo proporcionalno na osnovu remainingPortions.
     */
    private BigDecimal currentReservedOnOrder(Account account, Order order) {
        BigDecimal total = order.getReservedAmount();
        if (total == null) {
            return BigDecimal.ZERO;
        }
        Integer quantity = order.getQuantity();
        Integer remaining = order.getRemainingPortions();
        if (quantity == null || quantity <= 0 || remaining == null || remaining >= quantity) {
            // puna rezervacija (nije dirana ni jedan fill)
            return total.min(account.getReservedAmount());
        }
        BigDecimal ratio = new BigDecimal(remaining)
                .divide(new BigDecimal(quantity), 10, RoundingMode.HALF_UP);
        return total.multiply(ratio)
                .setScale(4, RoundingMode.HALF_UP)
                .min(account.getReservedAmount());
    }
}
