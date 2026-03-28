package rs.raf.banka2_bek.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.order.model.Order;

/**
 * Servis za validaciju All-or-None (AON) naloga.
 *
 * Specifikacija: Celina 3 - AON Order Validation
 *
 * AON nalog se moze izvrsiti SAMO ako je moguce popuniti celu kolicinu odjednom.
 * Ako nema dovoljno volumena za kompletno izvrsavanje, nalog ceka do sledeceg ciklusa.
 * Parcijalni fill-ovi NISU dozvoljeni za AON naloge.
 */
@Service
public class AonValidationService {

    private static final Logger log = LoggerFactory.getLogger(AonValidationService.class);

    /**
     * Proverava da li se AON nalog moze izvrsiti sa datim raspolozivim volumenom.
     *
     * @param order           nalog koji se proverava
     * @param availableVolume raspolozivi volume za fill (izracunat na osnovu listing volumena)
     * @return true ako se nalog moze izvrsiti, false ako ne moze (AON uslov nije ispunjen)
     */
    public boolean checkCanExecuteAon(Order order, int availableVolume) {
        /*
         * TODO: Implementirati AON validaciju
         *
         * 1. Proveriti da li je nalog AON:
         *    if (!order.isAllOrNone()) {
         *        return true;  // Nije AON — parcijalni fill-ovi su dozvoljeni
         *    }
         *
         * 2. Za AON naloge:
         *    - Proveriti da li raspolozivi volume pokriva CELU preostalu kolicinu:
         *      boolean canFill = availableVolume >= order.getRemainingPortions();
         *
         *    - Ako NE moze da se popuni kompletno:
         *      log.debug("AON order #{} cannot execute: available volume {} < remaining {}",
         *                order.getId(), availableVolume, order.getRemainingPortions());
         *      return false;
         *
         *    - Ako MOZE:
         *      log.debug("AON order #{} can execute: available volume {} >= remaining {}",
         *                order.getId(), availableVolume, order.getRemainingPortions());
         *      return true;
         *
         * 3. Edge cases:
         *    - availableVolume moze biti 0 (nema trzisnog volumena) — return false za AON
         *    - remainingPortions moze biti 0 (nalog vec zavrsen) — ne bi trebalo da stigne
         *      ovde, ali return true za sigurnost
         *    - Za MARKET AON nalog: mora da se popuni ceo nalog po trenutnoj trzisnoj ceni
         *    - Za LIMIT AON nalog: mora da se popuni ceo nalog, ALI samo ako je cena povoljnija
         *      od limit cene (provera cene se radi u OrderExecutionService, ne ovde)
         *    - order.getQuantity() vs order.getRemainingPortions():
         *      Koristiti remainingPortions jer AON zahteva da se SVE PREOSTALO popuni odjednom.
         *      Za potpuno novi nalog, remainingPortions == quantity.
         */
        if (!order.isAllOrNone()) {
            return true;
        }
        boolean canFill = availableVolume >= order.getRemainingPortions();
        if (!canFill) {
            log.debug("AON order #{} cannot execute: available {} < remaining {}",
                    order.getId(), availableVolume, order.getRemainingPortions());
        }
        return canFill;
    }
}
