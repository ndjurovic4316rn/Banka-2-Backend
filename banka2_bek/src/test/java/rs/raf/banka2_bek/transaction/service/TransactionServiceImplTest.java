package rs.raf.banka2_bek.transaction.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.transaction.dto.TransactionResponseDto;
import rs.raf.banka2_bek.transaction.dto.TransactionType;
import rs.raf.banka2_bek.transaction.model.Transaction;
import rs.raf.banka2_bek.transaction.repository.TransactionRepository;
import rs.raf.banka2_bek.transaction.service.implementation.TransactionServiceImpl;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    @Test
    void getReceiptTransaction_returnsOwnedTransaction() {
        Transaction transaction = Transaction.builder().id(42L).build();

        when(transactionRepository.findReceiptTransactionForClient(42L, 10L))
                .thenReturn(Optional.of(transaction));

        TransactionResponseDto result = transactionService.getReceiptTransaction(42L, 10L);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getType()).isEqualTo(TransactionType.TRANSFER);
    }

    @Test
    void getReceiptTransaction_throwsWhenNotOwnedOrMissing() {
        when(transactionRepository.findReceiptTransactionForClient(42L, 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getReceiptTransaction(42L, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction with ID 42 not found for authenticated client");
    }
}


