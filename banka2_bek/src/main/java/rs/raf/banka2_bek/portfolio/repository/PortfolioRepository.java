package rs.raf.banka2_bek.portfolio.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.portfolio.model.Portfolio;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Portfolio p WHERE p.id = :id")
    Optional<Portfolio> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Portfolio p WHERE p.userId = :userId AND p.listingId = :listingId")
    Optional<Portfolio> findByUserIdAndListingIdForUpdate(@Param("userId") Long userId,
                                                          @Param("listingId") Long listingId);
}
