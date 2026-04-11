package rs.raf.banka2_bek.tax.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.tax.model.TaxRecord;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRecordRepository extends JpaRepository<TaxRecord, Long> {

    Optional<TaxRecord> findByUserIdAndUserType(Long userId, String userType);

    @Query("SELECT t FROM TaxRecord t WHERE " +
           "(:name IS NULL OR LOWER(t.userName) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:userType IS NULL OR t.userType = :userType)")
    List<TaxRecord> findByFilters(@Param("name") String name, @Param("userType") String userType);
}
