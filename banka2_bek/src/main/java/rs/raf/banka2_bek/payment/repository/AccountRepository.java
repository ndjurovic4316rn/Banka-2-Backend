package rs.raf.banka2_bek.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.account.model.Account;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountNumber(String accountNumber);
}
