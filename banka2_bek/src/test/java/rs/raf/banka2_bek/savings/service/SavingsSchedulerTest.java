package rs.raf.banka2_bek.savings.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.savings.entity.SavingsDeposit;
import rs.raf.banka2_bek.savings.entity.SavingsDepositStatus;
import rs.raf.banka2_bek.savings.entity.SavingsInterestRate;
import rs.raf.banka2_bek.savings.entity.SavingsTransaction;
import rs.raf.banka2_bek.savings.repository.SavingsDepositRepository;
import rs.raf.banka2_bek.savings.repository.SavingsTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavingsSchedulerTest {

    @Mock SavingsDepositRepository depositRepo;
    @Mock SavingsTransactionRepository txRepo;
    @Mock SavingsInterestRateService rateService;
    @Mock AccountRepository accountRepo;
    @InjectMocks SavingsScheduler scheduler;

    private Currency rsd;
    private SavingsDeposit deposit;
    private Account linked;

    @BeforeEach
    void setUp() {
        rsd = new Currency();
        rsd.setId(1L);
        rsd.setCode("RSD");

        deposit = SavingsDeposit.builder()
                .id(1L).clientId(1L).linkedAccountId(10L)
                .principalAmount(new BigDecimal("100000"))
                .currency(rsd).termMonths(12)
                .annualInterestRate(new BigDecimal("4.00"))
                .startDate(LocalDate.of(2026, 5, 12))
                .maturityDate(LocalDate.of(2027, 5, 12))
                .nextInterestPaymentDate(LocalDate.of(2026, 6, 12))
                .totalInterestPaid(BigDecimal.ZERO)
                .autoRenew(false)
                .status(SavingsDepositStatus.ACTIVE).build();

        linked = new Account();
        linked.setId(10L);
        linked.setBalance(new BigDecimal("1000"));
        linked.setAvailableBalance(new BigDecimal("1000"));
        linked.setCurrency(rsd);
    }

    @Test
    void payMonthlyInterest_creditsLinkedAndUpdatesNextDate() {
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(linked));
        when(accountRepo.save(linked)).thenReturn(linked);
        when(depositRepo.save(deposit)).thenReturn(deposit);

        scheduler.payMonthlyInterest(deposit, LocalDate.of(2026, 6, 12));

        // 100000 * 4.00 / 1200 = 333.3333
        assertThat(linked.getBalance()).isEqualByComparingTo("1333.3333");
        assertThat(linked.getAvailableBalance()).isEqualByComparingTo("1333.3333");
        assertThat(deposit.getNextInterestPaymentDate()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(deposit.getTotalInterestPaid()).isEqualByComparingTo("333.3333");
        verify(txRepo).save(any(SavingsTransaction.class));
    }

    @Test
    void returnPrincipal_setsStatusMatured() {
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(linked));
        when(accountRepo.save(linked)).thenReturn(linked);
        when(depositRepo.save(deposit)).thenReturn(deposit);

        scheduler.returnPrincipal(deposit, LocalDate.of(2027, 5, 12));

        assertThat(deposit.getStatus()).isEqualTo(SavingsDepositStatus.MATURED);
        // 1000 + 100000 = 101000
        assertThat(linked.getBalance()).isEqualByComparingTo("101000");
        verify(txRepo).save(any(SavingsTransaction.class));
    }

    @Test
    void renewDeposit_createsNewWithCurrentRate() {
        SavingsInterestRate currentRate = SavingsInterestRate.builder()
                .id(2L).currency(rsd).termMonths(12).annualRate(new BigDecimal("4.50"))
                .active(true).effectiveFrom(LocalDate.now()).build();
        when(rateService.findActive(1L, 12)).thenReturn(Optional.of(currentRate));
        when(depositRepo.save(any(SavingsDeposit.class))).thenAnswer(inv -> inv.getArgument(0));

        scheduler.renewDeposit(deposit, LocalDate.of(2027, 5, 12));

        assertThat(deposit.getStatus()).isEqualTo(SavingsDepositStatus.RENEWED);
        // old (status RENEWED) + new deposit = 2 saves
        verify(depositRepo, times(2)).save(any(SavingsDeposit.class));
        verify(txRepo).save(any(SavingsTransaction.class));
    }

    @Test
    void runSavingsCycle_processesNoDeposits() {
        when(depositRepo.findByStatusAndNextInterestPaymentDateLessThanEqual(
                any(SavingsDepositStatus.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(depositRepo.findByStatusAndMaturityDateLessThanEqual(
                any(SavingsDepositStatus.class), any(LocalDate.class)))
                .thenReturn(List.of());

        scheduler.runSavingsCycle();

        verify(txRepo, never()).save(any());
    }

    @Test
    void runSavingsCycle_autoRenew_callsRenew() {
        deposit.setAutoRenew(true);
        deposit.setMaturityDate(LocalDate.now().minusDays(1));

        SavingsInterestRate rate = SavingsInterestRate.builder()
                .id(2L).currency(rsd).termMonths(12).annualRate(new BigDecimal("4.00"))
                .active(true).effectiveFrom(LocalDate.now()).build();

        when(depositRepo.findByStatusAndNextInterestPaymentDateLessThanEqual(
                any(SavingsDepositStatus.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(depositRepo.findByStatusAndMaturityDateLessThanEqual(
                any(SavingsDepositStatus.class), any(LocalDate.class)))
                .thenReturn(List.of(deposit));
        when(rateService.findActive(1L, 12)).thenReturn(Optional.of(rate));
        when(depositRepo.save(any(SavingsDeposit.class))).thenAnswer(inv -> inv.getArgument(0));

        scheduler.runSavingsCycle();

        assertThat(deposit.getStatus()).isEqualTo(SavingsDepositStatus.RENEWED);
        verify(txRepo, times(1)).save(any(SavingsTransaction.class));
    }

    @Test
    void runSavingsCycle_notAutoRenew_callsReturnPrincipal() {
        deposit.setAutoRenew(false);
        deposit.setMaturityDate(LocalDate.now().minusDays(1));

        when(depositRepo.findByStatusAndNextInterestPaymentDateLessThanEqual(
                any(SavingsDepositStatus.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(depositRepo.findByStatusAndMaturityDateLessThanEqual(
                any(SavingsDepositStatus.class), any(LocalDate.class)))
                .thenReturn(List.of(deposit));
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(linked));
        when(accountRepo.save(linked)).thenReturn(linked);
        when(depositRepo.save(deposit)).thenReturn(deposit);

        scheduler.runSavingsCycle();

        assertThat(deposit.getStatus()).isEqualTo(SavingsDepositStatus.MATURED);
        verify(txRepo, times(1)).save(any(SavingsTransaction.class));
    }
}
