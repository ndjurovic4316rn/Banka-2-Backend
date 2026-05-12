package rs.raf.banka2_bek.savings.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserResolver;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.otp.service.OtpService;
import rs.raf.banka2_bek.savings.dto.OpenDepositDto;
import rs.raf.banka2_bek.savings.dto.SavingsDepositDto;
import rs.raf.banka2_bek.savings.dto.ToggleAutoRenewDto;
import rs.raf.banka2_bek.savings.dto.WithdrawEarlyDto;
import rs.raf.banka2_bek.savings.entity.SavingsDeposit;
import rs.raf.banka2_bek.savings.entity.SavingsDepositStatus;
import rs.raf.banka2_bek.savings.entity.SavingsInterestRate;
import rs.raf.banka2_bek.savings.mapper.SavingsMapper;
import rs.raf.banka2_bek.savings.repository.SavingsDepositRepository;
import rs.raf.banka2_bek.savings.repository.SavingsTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavingsDepositServiceTest {

    @Mock SavingsDepositRepository depositRepo;
    @Mock SavingsTransactionRepository txRepo;
    @Mock SavingsInterestRateService rateService;
    @Mock SavingsMapper mapper;
    @Mock AccountRepository accountRepo;
    @Mock UserResolver userResolver;
    @Mock OtpService otpService;

    @InjectMocks SavingsDepositService service;

    private Currency rsd;
    private Client client;
    private Account source;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("stefan@gmail.com", "x", List.of()));

        rsd = new Currency();
        rsd.setId(1L);
        rsd.setCode("RSD");

        client = new Client();
        client.setId(1L);

        source = new Account();
        source.setId(10L);
        source.setAccountNumber("222000132199251311");
        source.setBalance(new BigDecimal("500000"));
        source.setAvailableBalance(new BigDecimal("500000"));
        source.setCurrency(rsd);
        source.setStatus(AccountStatus.ACTIVE);
        source.setClient(client);

        ReflectionTestUtils.setField(service, "bankRegistrationNumber", "22200022");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // -----------------------------------------------------------------------
    // openDeposit tests
    // -----------------------------------------------------------------------

    @Test
    void openDeposit_employeeCannotOpen() {
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(1L, UserRole.EMPLOYEE));

        OpenDepositDto dto = OpenDepositDto.builder()
                .sourceAccountId(10L).linkedAccountId(10L)
                .principalAmount(new BigDecimal("50000"))
                .termMonths(12)
                .autoRenew(false).otpCode("123456")
                .build();

        assertThatThrownBy(() -> service.openDeposit(dto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("klijenti");
    }

    @Test
    void openDeposit_invalidTerm_throws() {
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(1L, UserRole.CLIENT));
        when(otpService.verify(anyString(), eq("123456"))).thenReturn(Map.of("verified", true));

        OpenDepositDto dto = OpenDepositDto.builder()
                .sourceAccountId(10L).linkedAccountId(10L)
                .principalAmount(new BigDecimal("50000"))
                .termMonths(7) // not in {3,6,12,24,36}
                .autoRenew(false).otpCode("123456")
                .build();

        assertThatThrownBy(() -> service.openDeposit(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3, 6, 12, 24, 36");
    }

    @Test
    void openDeposit_belowMinimum_throws() {
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(1L, UserRole.CLIENT));
        when(otpService.verify(anyString(), eq("123456"))).thenReturn(Map.of("verified", true));
        // source account lookup (term=12 is valid, so passes VALID_TERMS check)
        when(accountRepo.findById(10L)).thenReturn(Optional.of(source));

        OpenDepositDto dto = OpenDepositDto.builder()
                .sourceAccountId(10L).linkedAccountId(10L)
                .principalAmount(new BigDecimal("5000")) // < 10000 RSD minimum
                .termMonths(12)
                .autoRenew(false).otpCode("123456")
                .build();

        assertThatThrownBy(() -> service.openDeposit(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minimalan iznos");
    }

    @Test
    void openDeposit_otpFailed_throws() {
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(1L, UserRole.CLIENT));
        when(otpService.verify(anyString(), eq("000000"))).thenReturn(Map.of("verified", false));

        OpenDepositDto dto = OpenDepositDto.builder()
                .sourceAccountId(10L).linkedAccountId(10L)
                .principalAmount(new BigDecimal("50000"))
                .termMonths(12)
                .autoRenew(false).otpCode("000000")
                .build();

        assertThatThrownBy(() -> service.openDeposit(dto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("OTP");
    }

    @Test
    void openDeposit_insufficientBalance_throws() {
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(1L, UserRole.CLIENT));
        when(otpService.verify(anyString(), eq("123456"))).thenReturn(Map.of("verified", true));

        source.setAvailableBalance(new BigDecimal("5000")); // less than requested
        when(accountRepo.findById(10L)).thenReturn(Optional.of(source));

        SavingsInterestRate rate = SavingsInterestRate.builder()
                .id(1L).currency(rsd).termMonths(12).annualRate(new BigDecimal("4.00"))
                .active(true).effectiveFrom(LocalDate.now()).build();
        when(rateService.findActive(1L, 12)).thenReturn(Optional.of(rate));

        OpenDepositDto dto = OpenDepositDto.builder()
                .sourceAccountId(10L).linkedAccountId(10L)
                .principalAmount(new BigDecimal("50000")) // > 5000 available
                .termMonths(12)
                .autoRenew(false).otpCode("123456")
                .build();

        assertThatThrownBy(() -> service.openDeposit(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nedovoljno");
    }

    // -----------------------------------------------------------------------
    // listMyDeposits
    // -----------------------------------------------------------------------

    @Test
    void listMyDeposits_returnsOwned() {
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(1L, UserRole.CLIENT));
        SavingsDeposit d = SavingsDeposit.builder().id(1L).clientId(1L).build();
        SavingsDepositDto dto = SavingsDepositDto.builder().id(1L).build();
        when(depositRepo.findByClientIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(d));
        when(mapper.toDepositDto(d)).thenReturn(dto);

        List<SavingsDepositDto> result = service.listMyDeposits();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void listMyDeposits_empty() {
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(2L, UserRole.CLIENT));
        when(depositRepo.findByClientIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());

        List<SavingsDepositDto> result = service.listMyDeposits();
        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // getDeposit
    // -----------------------------------------------------------------------

    @Test
    void getDeposit_clientOwnsIt_returnsDto() {
        SavingsDeposit d = SavingsDeposit.builder().id(1L).clientId(1L).status(SavingsDepositStatus.ACTIVE).build();
        SavingsDepositDto dto = SavingsDepositDto.builder().id(1L).build();
        when(depositRepo.findById(1L)).thenReturn(Optional.of(d));
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(1L, UserRole.CLIENT));
        when(mapper.toDepositDto(d)).thenReturn(dto);

        SavingsDepositDto result = service.getDeposit(1L);
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getDeposit_employee_throws403() {
        SavingsDeposit d = SavingsDeposit.builder().id(1L).clientId(1L).status(SavingsDepositStatus.ACTIVE).build();
        when(depositRepo.findById(1L)).thenReturn(Optional.of(d));
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(99L, UserRole.EMPLOYEE));

        assertThatThrownBy(() -> service.getDeposit(1L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("admin");
    }

    // -----------------------------------------------------------------------
    // withdrawEarly
    // -----------------------------------------------------------------------

    @Test
    void withdrawEarly_notActive_throws() {
        SavingsDeposit d = SavingsDeposit.builder()
                .id(1L).clientId(1L)
                .status(SavingsDepositStatus.MATURED)
                .build();
        when(depositRepo.findById(1L)).thenReturn(Optional.of(d));
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(1L, UserRole.CLIENT));

        WithdrawEarlyDto dto = new WithdrawEarlyDto();
        dto.setOtpCode("123456");

        assertThatThrownBy(() -> service.withdrawEarly(1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nije aktivan");
    }

    @Test
    void withdrawEarly_notOwner_throws403() {
        SavingsDeposit d = SavingsDeposit.builder()
                .id(1L).clientId(99L) // different client
                .status(SavingsDepositStatus.ACTIVE)
                .build();
        when(depositRepo.findById(1L)).thenReturn(Optional.of(d));
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(1L, UserRole.CLIENT));

        WithdrawEarlyDto dto = new WithdrawEarlyDto();
        dto.setOtpCode("123456");

        assertThatThrownBy(() -> service.withdrawEarly(1L, dto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Depozit ne pripada");
    }

    // -----------------------------------------------------------------------
    // toggleAutoRenew
    // -----------------------------------------------------------------------

    @Test
    void toggleAutoRenew_happyPath() {
        SavingsDeposit d = SavingsDeposit.builder()
                .id(1L).clientId(1L)
                .autoRenew(false)
                .status(SavingsDepositStatus.ACTIVE)
                .build();
        when(depositRepo.findById(1L)).thenReturn(Optional.of(d));
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(1L, UserRole.CLIENT));
        when(depositRepo.save(d)).thenReturn(d);
        SavingsDepositDto outDto = SavingsDepositDto.builder().id(1L).autoRenew(true).build();
        when(mapper.toDepositDto(d)).thenReturn(outDto);

        ToggleAutoRenewDto toggleDto = new ToggleAutoRenewDto();
        toggleDto.setAutoRenew(true);

        SavingsDepositDto result = service.toggleAutoRenew(1L, toggleDto);

        assertThat(d.getAutoRenew()).isTrue();
        verify(depositRepo).save(d);
        assertThat(result.getAutoRenew()).isTrue();
    }

    @Test
    void toggleAutoRenew_notActive_throws() {
        SavingsDeposit d = SavingsDeposit.builder()
                .id(1L).clientId(1L)
                .autoRenew(false)
                .status(SavingsDepositStatus.MATURED)
                .build();
        when(depositRepo.findById(1L)).thenReturn(Optional.of(d));
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(1L, UserRole.CLIENT));

        ToggleAutoRenewDto toggleDto = new ToggleAutoRenewDto();
        toggleDto.setAutoRenew(true);

        assertThatThrownBy(() -> service.toggleAutoRenew(1L, toggleDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aktivnim");
    }
}
