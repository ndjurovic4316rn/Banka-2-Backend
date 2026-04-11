package rs.raf.banka2_bek.transfers.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.transfers.model.Transfer;
import rs.raf.banka2_bek.client.model.Client;

import java.util.List;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    List<Transfer> findByCreatedByOrderByCreatedAtDesc(Client client);
}