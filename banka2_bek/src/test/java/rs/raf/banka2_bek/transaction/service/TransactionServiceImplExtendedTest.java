package rs.raf.banka2_bek.transaction.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.transaction.dto.TransactionListItemDto;
import rs.raf.banka2_bek.transaction.dto.TransactionResponseDto;
import rs.raf.banka2_bek.transaction.model.Transaction;
import rs.raf.banka2_bek.transaction.repository.TransactionRepository;
import rs.raf.banka2_bek.transaction.service.implementation.TransactionServiceImpl;
import rs.raf.banka2_bek.transfers.model.Transfer;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplExtendedTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private ClientRepository clientRepository;
    @InjectMocks private TransactionServiceImpl service;

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    void getTransactionById_found_returnsDto() {
        Account account = mock(Account.class);
        when(account.getAccountNumber()).thenReturn("111");
        Currency currency = mock(Currency.class);
        when(currency.getCode()).thenReturn("RSD");
        Payment payment = mock(Payment.class);
        when(payment.getToAccountNumber()).thenReturn("222");
        Transaction tx = Transaction.builder().id(1L).account(account).currency(currency).payment(payment)
                .description("Test").debit(BigDecimal.valueOf(500)).credit(BigDecimal.ZERO)
                .balanceAfter(BigDecimal.valueOf(500)).availableAfter(BigDecimal.valueOf(500)).build();
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
        TransactionResponseDto result = service.getTransactionById(1L);
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getToAccountNumber()).isEqualTo("222");
    }

    @Test
    void getTransactionById_notFound_throws() {
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTransactionById(999L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getTransactions_authenticated_returnsPage() {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(5L);
        User principal = new User("c@b.rs", "p", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));
        when(clientRepository.findByEmail("c@b.rs")).thenReturn(Optional.of(client));
        when(transactionRepository.findByAccountClientId(eq(5L), any(Pageable.class))).thenReturn(new PageImpl<>(Collections.emptyList()));
        Page<TransactionListItemDto> result = service.getTransactions(PageRequest.of(0, 10));
        assertThat(result).isEmpty();
    }

    @Test
    void getTransactions_notAuthenticated_throws() {
        assertThatThrownBy(() -> service.getTransactions(PageRequest.of(0, 10))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getTransactions_stringPrincipal_throws() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("str", null, Collections.emptyList()));
        assertThatThrownBy(() -> service.getTransactions(PageRequest.of(0, 10))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getTransactions_clientNotInDb_throws() {
        User principal = new User("x@b.rs", "p", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));
        when(clientRepository.findByEmail("x@b.rs")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTransactions(PageRequest.of(0, 10))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordPaymentSettlement_createsTwoTransactions() {
        Account from = mock(Account.class); when(from.getBalance()).thenReturn(BigDecimal.valueOf(500));
        when(from.getAvailableBalance()).thenReturn(BigDecimal.valueOf(500)); when(from.getAccountNumber()).thenReturn("111");
        Currency fc = mock(Currency.class); when(fc.getCode()).thenReturn("RSD"); when(from.getCurrency()).thenReturn(fc);
        Account to = mock(Account.class); when(to.getBalance()).thenReturn(BigDecimal.valueOf(1500));
        when(to.getAvailableBalance()).thenReturn(BigDecimal.valueOf(1500)); when(to.getAccountNumber()).thenReturn("222");
        Currency tc = mock(Currency.class); when(tc.getCode()).thenReturn("RSD"); when(to.getCurrency()).thenReturn(tc);
        Payment pmt = mock(Payment.class); when(pmt.getFromAccount()).thenReturn(from); when(pmt.getAmount()).thenReturn(BigDecimal.valueOf(1000));
        when(pmt.getPurpose()).thenReturn("Test"); when(pmt.getToAccountNumber()).thenReturn("222");
        when(transactionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        List<TransactionResponseDto> result = service.recordPaymentSettlement(pmt, to, mock(Client.class), BigDecimal.valueOf(1000));
        assertThat(result).hasSize(2);
    }

    @Test
    void getReceiptTransaction_found() {
        Account a = mock(Account.class); when(a.getAccountNumber()).thenReturn("111");
        Currency c = mock(Currency.class); when(c.getCode()).thenReturn("RSD");
        Transfer tr = mock(Transfer.class); Account ta = mock(Account.class); when(ta.getAccountNumber()).thenReturn("222"); when(tr.getToAccount()).thenReturn(ta);
        Transaction tx = Transaction.builder().id(10L).account(a).currency(c).transfer(tr).debit(BigDecimal.ZERO).credit(BigDecimal.valueOf(500)).balanceAfter(BigDecimal.valueOf(1500)).availableAfter(BigDecimal.valueOf(1500)).build();
        when(transactionRepository.findReceiptTransactionForClient(10L, 5L)).thenReturn(Optional.of(tx));
        assertThat(service.getReceiptTransaction(10L, 5L).getId()).isEqualTo(10L);
    }

    @Test
    void getReceiptTransaction_notFound_throws() {
        when(transactionRepository.findReceiptTransactionForClient(99L, 5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getReceiptTransaction(99L, 5L)).isInstanceOf(IllegalArgumentException.class);
    }
}
