package rs.raf.banka2_bek.order.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.order.exception.InsufficientFundsException;
import rs.raf.banka2_bek.order.exception.InsufficientHoldingsException;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundReservationServiceTest {

    @Mock
    AccountRepository accountRepository;

    @Mock
    PortfolioRepository portfolioRepository;

    @InjectMocks
    FundReservationService service;

    // ── BUY helpers ──────────────────────────────────────────────────────────
    private Account buyAccount(BigDecimal balance, BigDecimal available, BigDecimal reserved) {
        Account a = new Account();
        a.setId(1L);
        a.setAccountNumber("111");
        a.setBalance(balance);
        a.setAvailableBalance(available);
        a.setReservedAmount(reserved);
        return a;
    }

    private Order buyOrder(BigDecimal reservedAmount, Integer qty) {
        Order o = new Order();
        o.setId(100L);
        o.setReservedAmount(reservedAmount);
        o.setReservedAccountId(1L);
        o.setQuantity(qty);
        o.setRemainingPortions(qty);
        o.setReservationReleased(false);
        return o;
    }

    // ── reserveForBuy ────────────────────────────────────────────────────────
    @Test
    void reserveForBuy_reducesAvailableBalance_increasesReservedAmount() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO);
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        service.reserveForBuy(order, account);

        assertThat(account.getAvailableBalance()).isEqualByComparingTo("7500.00");
        assertThat(account.getReservedAmount()).isEqualByComparingTo("2500.00");
        assertThat(account.getBalance()).isEqualByComparingTo("10000.00");
        verify(accountRepository).save(account);
    }

    @Test
    void reserveForBuy_throwsInsufficientFundsException_whenAvailableBalanceTooLow() {
        Account account = buyAccount(new BigDecimal("1000.00"), new BigDecimal("1000.00"), BigDecimal.ZERO);
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.reserveForBuy(order, account))
                .isInstanceOf(InsufficientFundsException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void reserveForBuy_throwsIllegalStateException_whenReservationAlreadyReleased() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO);
        Order order = buyOrder(new BigDecimal("2500.00"), 10);
        order.setReservationReleased(true);

        assertThatThrownBy(() -> service.reserveForBuy(order, account))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── releaseForBuy ────────────────────────────────────────────────────────
    @Test
    void releaseForBuy_restoresAvailableBalance_zerosReservation_setsReleasedFlag() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("7500.00"), new BigDecimal("2500.00"));
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        service.releaseForBuy(order);

        assertThat(account.getAvailableBalance()).isEqualByComparingTo("10000.00");
        assertThat(account.getReservedAmount()).isEqualByComparingTo("0.00");
        assertThat(order.isReservationReleased()).isTrue();
        verify(accountRepository).save(account);
    }

    @Test
    void releaseForBuy_isIdempotent_whenAlreadyReleased() {
        Order order = buyOrder(new BigDecimal("2500.00"), 10);
        order.setReservationReleased(true);

        service.releaseForBuy(order);

        verify(accountRepository, never()).findForUpdateById(any());
        verify(accountRepository, never()).save(any());
        assertThat(order.isReservationReleased()).isTrue();
    }

    // ── consumeForBuyFill ────────────────────────────────────────────────────
    @Test
    void consumeForBuyFill_reducesBalanceAndReservationProportionally() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("7500.00"), new BigDecimal("2500.00"));
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        service.consumeForBuyFill(order, 4, new BigDecimal("1000.00"));

        // balance = 10000 - 1000 (stvarna cena fill-a)
        assertThat(account.getBalance()).isEqualByComparingTo("9000.00");
        // reserved smanjen proporcionalno: 4/10 * 2500 = 1000 → reserved 2500 - 1000 = 1500
        assertThat(account.getReservedAmount()).isEqualByComparingTo("1500.00");
        verify(accountRepository).save(account);
    }

    @Test
    void consumeForBuyFill_releasesFullyWhenLastPortion() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("7500.00"), new BigDecimal("2500.00"));
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        service.consumeForBuyFill(order, 10, new BigDecimal("2400.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("7600.00");
        assertThat(account.getReservedAmount()).isEqualByComparingTo("0.00");
    }

    // ── reserveForSell ───────────────────────────────────────────────────────
    private Portfolio portfolio(int quantity, int reserved) {
        Portfolio p = new Portfolio();
        p.setId(1L);
        p.setUserId(42L);
        p.setListingId(7L);
        p.setQuantity(quantity);
        p.setReservedQuantity(reserved);
        return p;
    }

    @Test
    void reserveForSell_increasesReservedQuantity() {
        Portfolio p = portfolio(30, 0);
        Order order = new Order();
        order.setId(200L);
        order.setQuantity(5);
        order.setRemainingPortions(5);

        service.reserveForSell(order, p);

        assertThat(p.getReservedQuantity()).isEqualTo(5);
        assertThat(p.getAvailableQuantity()).isEqualTo(25);
        verify(portfolioRepository).save(p);
    }

    @Test
    void reserveForSell_throwsInsufficientHoldings_whenAvailableQuantityTooLow() {
        Portfolio p = portfolio(30, 27);
        Order order = new Order();
        order.setQuantity(5);

        assertThatThrownBy(() -> service.reserveForSell(order, p))
                .isInstanceOf(InsufficientHoldingsException.class);

        verify(portfolioRepository, never()).save(any());
    }

    // ── releaseForSell ───────────────────────────────────────────────────────
    @Test
    void releaseForSell_decreasesReservedQuantity_setsReleasedFlag() {
        Portfolio p = portfolio(30, 5);
        Order order = new Order();
        order.setId(201L);
        order.setQuantity(5);
        order.setRemainingPortions(5);
        order.setReservationReleased(false);

        service.releaseForSell(order, p);

        assertThat(p.getReservedQuantity()).isEqualTo(0);
        assertThat(order.isReservationReleased()).isTrue();
        verify(portfolioRepository).save(p);
    }

    @Test
    void releaseForSell_isIdempotent() {
        Portfolio p = portfolio(30, 5);
        Order order = new Order();
        order.setQuantity(5);
        order.setRemainingPortions(5);
        order.setReservationReleased(true);

        service.releaseForSell(order, p);

        assertThat(p.getReservedQuantity()).isEqualTo(5);
        verify(portfolioRepository, never()).save(any());
    }

    // ── consumeForSellFill ───────────────────────────────────────────────────
    @Test
    void consumeForSellFill_reducesQuantityAndReservedProportionally() {
        Portfolio p = portfolio(30, 5);
        Order order = new Order();
        order.setId(202L);
        order.setQuantity(5);
        order.setRemainingPortions(5);

        service.consumeForSellFill(order, p, 2);

        assertThat(p.getQuantity()).isEqualTo(28);
        assertThat(p.getReservedQuantity()).isEqualTo(3);
        verify(portfolioRepository).save(p);
    }
}
