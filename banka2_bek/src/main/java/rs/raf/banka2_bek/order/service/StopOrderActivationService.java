package rs.raf.banka2_bek.order.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Servis za aktiviranje STOP i STOP_LIMIT naloga kada trzisna cena
 * dostigne zadatu stop vrednost.
 *
 * Specifikacija: Celina 3 - Stop/Stop-Limit Order aktivacija
 *
 * Ponasanje:
 * - STOP BUY:  aktivira se kada listing.price >= order.stopValue
 * - STOP SELL: aktivira se kada listing.price <= order.stopValue
 * - STOP nalog se pretvara u MARKET nalog pri aktivaciji
 * - STOP_LIMIT nalog se pretvara u LIMIT nalog pri aktivaciji
 */
@Service
@RequiredArgsConstructor
public class StopOrderActivationService {

    private static final Logger log = LoggerFactory.getLogger(StopOrderActivationService.class);

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;

    /**
     * Proverava sve APPROVED STOP i STOP_LIMIT naloge i aktivira ih
     * ako je trzisna cena dostigla stop vrednost.
     *
     * Poziva se periodicno iz OrderScheduler-a (svakih 30 sekundi).
     */
    @Transactional
    public void checkAndActivateStopOrders() {
        /*
         * TODO: Implementirati proveru i aktivaciju stop naloga
         *
         * 1. Dohvatiti sve APPROVED naloge koji nisu zavrseni (isDone=false):
         *    List<Order> stopOrders = orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED);
         *    Filtrirati samo one gde orderType == STOP ili orderType == STOP_LIMIT.
         *
         * 2. Za svaki stop nalog:
         *    a. Dohvatiti azuriranu cenu listinga:
         *       Listing listing = listingRepository.findById(order.getListing().getId()).orElse(null);
         *       Ako listing ne postoji, loguj upozorenje i preskoci.
         *
         *    b. Dohvatiti trenutnu trzisnu cenu:
         *       BigDecimal currentPrice = listing.getPrice();
         *       Ako je currentPrice null, preskoci.
         *
         *    c. Proveriti da li je stop uslov ispunjen:
         *       - Za BUY smer (order.getDirection() == OrderDirection.BUY):
         *         Uslov: currentPrice.compareTo(order.getStopValue()) >= 0
         *         (cena je dostigla ili presla stop vrednost)
         *       - Za SELL smer (order.getDirection() == OrderDirection.SELL):
         *         Uslov: currentPrice.compareTo(order.getStopValue()) <= 0
         *         (cena je pala na ili ispod stop vrednosti)
         *
         *    d. Ako je uslov ispunjen, aktivirati nalog:
         *       - Ako je orderType == STOP:
         *         order.setOrderType(OrderType.MARKET);
         *         order.setPricePerUnit(currentPrice);  // azuriraj na trenutnu trzisnu cenu
         *       - Ako je orderType == STOP_LIMIT:
         *         order.setOrderType(OrderType.LIMIT);
         *         order.setPricePerUnit(order.getLimitValue());  // koristi limit cenu
         *
         *    e. Azurirati lastModification:
         *       order.setLastModification(LocalDateTime.now());
         *
         *    f. Sacuvati nalog:
         *       orderRepository.save(order);
         *
         *    g. Logovati aktivaciju:
         *       log.info("Stop order #{} activated: {} -> {}, trigger price: {}, stop value: {}",
         *                order.getId(), originalType, order.getOrderType(),
         *                currentPrice, order.getStopValue());
         *
         * 3. Edge cases:
         *    - Listing moze biti obrisan — handle gracefully sa logom i continue
         *    - stopValue moze biti null (ne bi trebalo za STOP/STOP_LIMIT) — preskoci uz warning
         *    - Cena moze biti 0 ili negativna za neke instrumente — ne aktiviraj u tom slucaju
         *    - Vise naloga mogu imati isti listing — svi se aktiviraju nezavisno
         *    - Transakcioni integritet: @Transactional osigurava konzistentnost
         */
        log.info("Stop order activation check - TODO: implementirati");
    }
}
