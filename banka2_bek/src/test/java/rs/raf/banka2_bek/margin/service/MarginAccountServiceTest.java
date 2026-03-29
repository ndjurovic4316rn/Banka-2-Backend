package rs.raf.banka2_bek.margin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.margin.dto.CreateMarginAccountDto;
import rs.raf.banka2_bek.margin.dto.MarginAccountDto;
import rs.raf.banka2_bek.margin.model.MarginAccount;
import rs.raf.banka2_bek.margin.model.MarginAccountStatus;
import rs.raf.banka2_bek.margin.model.MarginTransaction;
import rs.raf.banka2_bek.margin.model.MarginTransactionType;
import rs.raf.banka2_bek.margin.repository.MarginAccountRepository;
import rs.raf.banka2_bek.margin.repository.MarginTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarginAccountServiceTest {

    @Mock
    private MarginAccountRepository marginAccountRepository;
    @Mock
    private MarginTransactionRepository marginTransactionRepository;
    @Mock
    private AccountRepository accountRepository;

    private MarginAccountService marginAccountService;

    @BeforeEach
    void setUp() {
        marginAccountService = new MarginAccountService(
                marginAccountRepository,
                marginTransactionRepository,
                accountRepository
        );
    }

    @Test
    void createForUser_success_calculatesAndPersistsAllValues() {
        Long userId = 10L;
        Account baseAccount = activeAccount(1L, userId, "10000.0000", "10000.0000");
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("5000.00"));

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(baseAccount));
        when(marginAccountRepository.findByAccountId(1L)).thenReturn(List.of());
        when(marginAccountRepository.save(any(MarginAccount.class))).thenAnswer(invocation -> {
            MarginAccount marginAccount = invocation.getArgument(0);
            marginAccount.setId(55L);
            marginAccount.setCreatedAt(LocalDateTime.now());
            return marginAccount;
        });

        MarginAccountDto result = marginAccountService.createForUser(userId, dto);

        assertThat(result.getId()).isEqualTo(55L);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getStatus()).isEqualTo(MarginAccountStatus.ACTIVE.name());
        assertThat(result.getBankParticipation()).isEqualByComparingTo("0.50");
        assertThat(result.getInitialMargin()).isEqualByComparingTo("10000.0000");
        assertThat(result.getLoanValue()).isEqualByComparingTo("5000.0000");
        assertThat(result.getMaintenanceMargin()).isEqualByComparingTo("5000.0000");

        assertThat(baseAccount.getAvailableBalance()).isEqualByComparingTo("5000.0000");
        assertThat(baseAccount.getBalance()).isEqualByComparingTo("5000.0000");

        ArgumentCaptor<MarginTransaction> txCaptor = ArgumentCaptor.forClass(MarginTransaction.class);
        verify(marginTransactionRepository).save(txCaptor.capture());

        MarginTransaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(MarginTransactionType.DEPOSIT);
        assertThat(tx.getAmount()).isEqualByComparingTo("5000.0000");
        assertThat(tx.getDescription()).isEqualTo("Initial margin deposit");
    }

    @Test
    void createForUser_throwsWhenUserIdMissing() {
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("100.00"));

        assertThatThrownBy(() -> marginAccountService.createForUser(null, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Authenticated user id is required.");
    }

    @Test
    void createForUser_throwsWhenDepositIsNonPositive() {
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, BigDecimal.ZERO);

        assertThatThrownBy(() -> marginAccountService.createForUser(10L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Initial deposit must be greater than zero.");
    }

    @Test
    void createForUser_throwsWhenAccountMissing() {
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("100.00"));
        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marginAccountService.createForUser(10L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account not found.");
    }

    @Test
    void createForUser_throwsWhenAccountOwnedByAnotherClient() {
        Account baseAccount = activeAccount(1L, 999L, "1000.00", "1000.00");
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("100.00"));

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(baseAccount));

        assertThatThrownBy(() -> marginAccountService.createForUser(10L, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You are not allowed to create a margin account for this base account.");
    }

    @Test
    void createForUser_throwsWhenBaseAccountIsInactive() {
        Account baseAccount = activeAccount(1L, 10L, "1000.00", "1000.00");
        baseAccount.setStatus(AccountStatus.BLOCKED);
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("100.00"));

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(baseAccount));

        assertThatThrownBy(() -> marginAccountService.createForUser(10L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Base account must be active.");
    }

    @Test
    void createForUser_throwsWhenMarginAccountAlreadyExists() {
        Account baseAccount = activeAccount(1L, 10L, "1000.00", "1000.00");
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("100.00"));

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(baseAccount));
        when(marginAccountRepository.findByAccountId(1L)).thenReturn(List.of(new MarginAccount()));

        assertThatThrownBy(() -> marginAccountService.createForUser(10L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Margin account already exists for this base account.");
    }

    @Test
    void createForUser_throwsWhenInsufficientAvailableBalance() {
        Account baseAccount = activeAccount(1L, 10L, "50.00", "50.00");
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("100.00"));

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(baseAccount));
        when(marginAccountRepository.findByAccountId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> marginAccountService.createForUser(10L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient available balance for initial margin deposit.");
    }

    private Account activeAccount(Long accountId, Long clientId, String available, String balance) {
        Client client = Client.builder().id(clientId).email("client@test.com").build();
        return Account.builder()
                .id(accountId)
                .client(client)
                .status(AccountStatus.ACTIVE)
                .availableBalance(new BigDecimal(available))
                .balance(new BigDecimal(balance))
                .build();
    }
}

