package rs.raf.banka2_bek.order.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.order.service.OrderExecutionService;
import rs.raf.banka2_bek.order.service.StopOrderActivationService;

/**
 * Scheduler komponenta koja periodicno pokrece proveru i izvrsavanje naloga.
 *
 * Specifikacija: Celina 3 - Order Execution Scheduler
 *
 * Dva schedulovana zadatka:
 * 1. processStopOrders — svakih 30 sekundi proverava STOP/STOP_LIMIT naloge
 * 2. executeApprovedOrders — svakih 10 sekundi izvrsava APPROVED MARKET/LIMIT naloge
 *
 * VAZNO: Potrebno je omoguciti @EnableScheduling na nivou aplikacije
 * (u glavnoj @SpringBootApplication klasi ili u posebnoj @Configuration klasi).
 */
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderScheduler.class);

    private final StopOrderActivationService stopOrderActivationService;
    private final OrderExecutionService orderExecutionService;

    /**
     * Proverava STOP i STOP_LIMIT naloge svakih 30 sekundi.
     * Ako trzisna cena dostigne stop vrednost, nalog se aktivira
     * (STOP -> MARKET, STOP_LIMIT -> LIMIT).
     */
    @Scheduled(fixedRate = 30000)
    public void processStopOrders() {
        /*
         * TODO: Implementirati poziv StopOrderActivationService
         *
         * 1. Obaviti poziv unutar try-catch bloka:
         *    try {
         *        stopOrderActivationService.checkAndActivateStopOrders();
         *    } catch (Exception e) {
         *        log.error("Error processing stop orders: {}", e.getMessage(), e);
         *    }
         *
         * 2. Opciono: dodati metriku (broj aktiviranih naloga po ciklusu)
         *    za monitoring putem Actuator-a ili custom metrike.
         *
         * 3. Opciono: dodati log na DEBUG nivou za pocetak i kraj ciklusa:
         *    log.debug("Stop order check cycle started");
         *    ...
         *    log.debug("Stop order check cycle completed");
         */
        log.info("TODO: implementirati"); // stub
    }

    /**
     * Izvrsava APPROVED naloge (MARKET i LIMIT) svakih 10 sekundi.
     * Za svaki nalog pokusava da izvrsi jedan parcijalni fill.
     */
    @Scheduled(fixedRate = 10000)
    public void executeApprovedOrders() {
        /*
         * TODO: Implementirati poziv OrderExecutionService
         *
         * 1. Obaviti poziv unutar try-catch bloka:
         *    try {
         *        orderExecutionService.executeOrders();
         *    } catch (Exception e) {
         *        log.error("Error executing approved orders: {}", e.getMessage(), e);
         *    }
         *
         * 2. Opciono: dodati metriku (broj izvrsenih fill-ova po ciklusu).
         *
         * 3. Opciono: proveriti da li je berza otvorena pre pokretanja:
         *    - Ako se ne trguje vikendom, preskociti ciklus (ali after-hours nalozi
         *      se ipak izvrsavaju van radnog vremena berze).
         *    - Za sada, izvrsavati uvek (24/7 simulacija).
         *
         * 4. Log na DEBUG nivou za pocetak/kraj ciklusa.
         */
        log.info("TODO: implementirati"); // stub
    }
}
