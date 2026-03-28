package rs.raf.banka2_bek.tax.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.tax.service.TaxService;

/**
 * Scheduler za automatski obracun poreza.
 *
 * Pokrece se prvog dana svakog meseca u ponoc (00:00:00).
 * Poziva TaxService.calculateTaxForAllUsers() koji obracunava porez
 * na osnovu svih DONE ordera za svakog korisnika.
 *
 * Specifikacija: Celina 3 - Porez na kapitalnu dobit (15%)
 *
 * TODO: Implementirati logiku:
 *   1. Logirati pocetak obracuna: "Pokrecem mesecni obracun poreza..."
 *   2. Pozvati taxService.calculateTaxForAllUsers()
 *   3. Logirati zavrsetak: "Mesecni obracun poreza zavrsen uspesno."
 *   4. Hendlati exception:
 *      - Uhvatiti Exception
 *      - Logirati gresku: "Greska pri obracunu poreza: {}", e.getMessage()
 *   5. TODO (buducnost): Poslati email notifikacije korisnicima sa novim poreskim obavezama
 *      - Za svakog korisnika ciji se taxOwed promenio, poslati email
 *      - Email template: "Postovani, obracunat vam je porez od X RSD za mesec Y"
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaxScheduler {

    private final TaxService taxService;

    /**
     * Mesecni obracun poreza — pokrece se 1. u mesecu u 00:00:00.
     *
     * Cron format: sekunda minut sat dan-u-mesecu mesec dan-u-nedelji
     * "0 0 0 1 * *" = 00:00:00 prvog dana svakog meseca
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void calculateMonthlyTax() {
        // TODO: Implementirati prema uputstvima iznad
        // log.info("Pokrecem mesecni obracun poreza...");
        // try {
        //     taxService.calculateTaxForAllUsers();
        //     log.info("Mesecni obracun poreza zavrsen uspesno.");
        // } catch (Exception e) {
        //     log.error("Greska pri obracunu poreza: {}", e.getMessage(), e);
        // }
        // TODO: Poslati email notifikacije korisnicima
        log.info("Monthly tax calculation started"); try { taxService.calculateTaxForAllUsers(); log.info("Monthly tax done"); } catch (Exception e) { log.error("Tax error: {}", e.getMessage()); }
    }
}
