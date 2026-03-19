package rs.raf.banka2_bek.transaction.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.transaction.dto.TransactionType;
import rs.raf.banka2_bek.transaction.model.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Page<Transaction> findByAccountClientId(Long clientId, Pageable pageable);

    @Query("""
            select t from Transaction t
            left join fetch t.account a
            left join fetch t.currency c
            left join fetch t.payment p
            left join fetch p.fromAccount fa
            left join fetch t.transfer tr
            where t.id = :transactionId
              and a.client.id = :clientId
            """)
    Optional<Transaction> findReceiptTransactionForClient(
            @Param("transactionId") Long transactionId,
            @Param("clientId") Long clientId
    );

    @Query("""
       select t from Transaction t
       left join t.payment p
       left join t.transfer tr
       where t.account.client.id = :clientId
         and (:fromDate is null or t.createdAt >= :fromDate)
         and (:toDate is null or t.createdAt <= :toDate)
         and (:minAmount is null or
              (case
                   when p is not null then p.amount
                   when tr is not null then tr.fromAmount
                   else null
               end) >= :minAmount)
         and (:maxAmount is null or
              (case
                   when p is not null then p.amount
                   when tr is not null then tr.fromAmount
                   else null
               end) <= :maxAmount)
         and (:type is null or
              (:type = 'PAYMENT' and p is not null) or
              (:type = 'TRANSFER' and tr is not null))
       """)
    Page<Transaction> findTransactionsByAccountUserIdAndFilters(
            @Param("clientId") Long clientId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("type") String type,
            Pageable pageable
    );
}
