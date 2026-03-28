package rs.raf.banka2_bek.order.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.transaction.model.Transaction;
import rs.raf.banka2_bek.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Servis za izvrsavanje odobrenih naloga (APPROVED).
 *
 * Specifikacija: Celina 3 - Order Execution Engine
 *
 * Simulira izvrsavanje naloga na berzi koristeci parcijalno punjenje (partial fills).
 * Podrzava: MARKET, LIMIT, AON (all-or-none), after-hours naloge.
 * STOP i STOP_LIMIT nalozi se ovde NE izvrsavaju — oni se prvo aktiviraju
 * u StopOrderActivationService pa postaju MARKET/LIMIT.
 *
 * Provizije po specifikaciji:
 * - MARKET: max(14% * price, $7)
 * - LIMIT:  max(24% * price, $12)
 * Provizija se uplacuje na racun banke.
 */
@Service
@RequiredArgsConstructor
public class OrderExecutionService {

    private static final Logger log = LoggerFactory.getLogger(OrderExecutionService.class);

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final AonValidationService aonValidationService;

    /** Provizija za MARKET naloge: max(14% * price, $7) */
    private static final BigDecimal MARKET_COMMISSION_RATE = new BigDecimal("0.14");
    private static final BigDecimal MARKET_COMMISSION_MIN = new BigDecimal("7");

    /** Provizija za LIMIT naloge: max(24% * price, $12) */
    private static final BigDecimal LIMIT_COMMISSION_RATE = new BigDecimal("0.24");
    private static final BigDecimal LIMIT_COMMISSION_MIN = new BigDecimal("12");

    /** Dodatan delay po fill-u za after-hours naloge (u minutima) */
    private static final int AFTER_HOURS_DELAY_MINUTES = 30;

    /**
     * Glavna metoda za izvrsavanje naloga.
     * Poziva se periodicno iz OrderScheduler-a (svakih 10 sekundi).
     *
     * Prolazi kroz sve APPROVED naloge koji nisu zavrseni i pokusava
     * da izvrsi jedan parcijalni fill po pozivu za svaki nalog.
     */
    @Transactional
    public void executeOrders() {
        /*
         * TODO: Implementirati glavnu petlju izvrsavanja naloga
         *
         * 1. Dohvatiti sve APPROVED naloge koji nisu zavrseni:
         *    List<Order> activeOrders = orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED);
         *
         * 2. Filtrirati samo MARKET i LIMIT naloge (STOP i STOP_LIMIT se ovde ne izvrsavaju
         *    jer ih StopOrderActivationService pretvara u MARKET/LIMIT pre izvrsavanja).
         *
         * 3. Za svaki nalog:
         *    a. Proveriti settlement date:
         *       - Ako listing ima settlementDate i taj datum je prosao (before LocalDate.now()):
         *         order.setStatus(OrderStatus.DECLINED);
         *         order.setIsDone(true);
         *         order.setLastModification(LocalDateTime.now());
         *         orderRepository.save(order);
         *         log.warn("Order #{} auto-declined: settlement date {} has passed", ...);
         *         continue;
         *
         *    b. Proveriti after-hours delay:
         *       - Ako je order.isAfterHours() == true:
         *         Proveriti da li je proslo dovoljno vremena od poslednjeg fill-a
         *         (lastModification + AFTER_HOURS_DELAY_MINUTES minuta <= now)
         *         Ako nije proslo dovoljno vremena, preskoci ovaj nalog.
         *
         *    c. Pozvati executeSingleOrder(order);
         *
         * 4. Svaka greska u izvrsavanju jednog naloga NE SME da prekine
         *    izvrsavanje ostalih — wrap u try-catch sa log.error().
         */
        log.info("TODO: implementirati"); // stub
    }

    /**
     * Izvrsava jedan parcijalni fill za dati nalog.
     *
     * @param order nalog koji se izvrsava
     */
    private void executeSingleOrder(Order order) {
        /*
         * TODO: Implementirati izvrsavanje jednog naloga (partial fill)
         *
         * 1. Dohvatiti azuriranu cenu listinga:
         *    Listing listing = listingRepository.findById(order.getListing().getId())
         *        .orElseThrow(() -> ...);
         *
         * 2. Odrediti execution price:
         *    - Za MARKET: koristi listing.getAsk() (BUY) ili listing.getBid() (SELL)
         *    - Za LIMIT BUY:  izvrsi samo ako listing.getAsk() <= order.getLimitValue()
         *      Ako nije ispunjen uslov, return (ne izvrsavaj).
         *    - Za LIMIT SELL: izvrsi samo ako listing.getBid() >= order.getLimitValue()
         *      Ako nije ispunjen uslov, return (ne izvrsavaj).
         *
         * 3. Odrediti kolicinu za fill:
         *    a. Izracunati fillQuantity koristeci vremenski interval formulu:
         *       - volume = listing.getVolume() (dnevni volume listinga)
         *       - remaining = order.getRemainingPortions()
         *       - Ako volume == 0 ili volume == null, postavi volume = 1
         *       - maxInterval = (24 * 60) / (volume / remaining)  (u sekundama)
         *       - randomInterval = ThreadLocalRandom.current().nextInt(0, maxInterval + 1)
         *       - fillQuantity = Math.max(1, remaining / Math.max(1, randomInterval))
         *       - fillQuantity = Math.min(fillQuantity, remaining)  // ne moze vise od preostalog
         *
         *    b. AON (All-or-None) provera:
         *       - Pozvati aonValidationService.checkCanExecuteAon(order, fillQuantity)
         *       - Ako vrati false (order je AON a fillQuantity < quantity), return
         *       - Ako je AON, fillQuantity = order.getQuantity() (mora sve odjednom)
         *
         * 4. Izracunati ukupnu cenu i proviziju:
         *    BigDecimal totalPrice = executionPrice * fillQuantity * contractSize
         *    BigDecimal commission = calculateCommission(totalPrice, order.getOrderType())
         *
         * 5. Izvrsiti finansijske operacije:
         *    - Za BUY:
         *      a. updateAccountBalance(order, fillQuantity, executionPrice, commission)
         *         — skida totalPrice + commission sa korisnikovog racuna
         *      b. updatePortfolio(order, fillQuantity, executionPrice)
         *         — dodaje hartije u portfolio korisnika
         *      c. createFillTransaction(order, fillQuantity, executionPrice)
         *         — kreira Transaction zapis (debit na korisnikov racun)
         *
         *    - Za SELL:
         *      a. updatePortfolio(order, -fillQuantity, executionPrice)
         *         — skida hartije iz portfolija korisnika
         *      b. updateAccountBalance(order, fillQuantity, executionPrice, commission)
         *         — dodaje totalPrice - commission na korisnikov racun
         *      c. createFillTransaction(order, fillQuantity, executionPrice)
         *         — kreira Transaction zapis (credit na korisnikov racun)
         *
         * 6. Azurirati nalog:
         *    order.setRemainingPortions(order.getRemainingPortions() - fillQuantity);
         *    order.setLastModification(LocalDateTime.now());
         *    if (order.getRemainingPortions() <= 0) {
         *        order.setDone(true);
         *        order.setStatus(OrderStatus.DONE);
         *    }
         *    orderRepository.save(order);
         *
         * 7. Logovati:
         *    log.info("Order #{} filled {} of {} @ {} (remaining: {}, commission: {})",
         *             order.getId(), fillQuantity, order.getQuantity(),
         *             executionPrice, order.getRemainingPortions(), commission);
         */
        log.info("TODO: implementirati"); // stub
    }

    /**
     * Kreira Transaction zapis za jedan fill naloga.
     *
     * @param order    nalog koji se izvrsava
     * @param quantity kolicina hartija u ovom fill-u
     * @param price    cena po jedinici pri izvrsavanju
     */
    private void createFillTransaction(Order order, int quantity, BigDecimal price) {
        /*
         * TODO: Implementirati kreiranje Transaction zapisa
         *
         * 1. Dohvatiti Account korisnika:
         *    Account account = accountRepository.findById(order.getAccountId())
         *        .orElseThrow(() -> ...);
         *
         * 2. Izracunati iznose:
         *    BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity))
         *        .multiply(BigDecimal.valueOf(order.getContractSize()))
         *        .setScale(4, RoundingMode.HALF_UP);
         *
         * 3. Kreirati Transaction koristeci builder:
         *    Transaction.builder()
         *        .account(account)
         *        .currency(account.getCurrency())
         *        .description("Order #" + order.getId() + " fill: " + quantity + " x " +
         *                     order.getListing().getTicker() + " @ " + price)
         *        .debit(order.getDirection() == OrderDirection.BUY ? totalAmount : BigDecimal.ZERO)
         *        .credit(order.getDirection() == OrderDirection.SELL ? totalAmount : BigDecimal.ZERO)
         *        .balanceAfter(account.getBalance())    // azurirano stanje
         *        .availableAfter(account.getAvailableBalance())
         *        .createdAt(LocalDateTime.now())
         *        .build();
         *
         * 4. Sacuvati: transactionRepository.save(transaction);
         *
         * 5. Edge cases:
         *    - Account mora postojati (baciti exception ako ne postoji)
         *    - balanceAfter i availableAfter moraju odrazavati stanje NAKON transakcije
         *    - Provizija se NE zapisuje u ovu transakciju (zapisuje se posebno u updateAccountBalance)
         */
        log.info("TODO: implementirati"); // stub
    }

    /**
     * Azurira portfolio korisnika nakon fill-a.
     * Za BUY: dodaje hartije. Za SELL: oduzima hartije.
     *
     * @param order    nalog koji se izvrsava
     * @param quantity pozitivan za BUY, negativan za SELL
     * @param price    cena po jedinici pri izvrsavanju
     */
    private void updatePortfolio(Order order, int quantity, BigDecimal price) {
        /*
         * TODO: Implementirati azuriranje portfolija
         *
         * 1. Potraziti postojeci portfolio entry za korisnika i listing:
         *    Optional<Portfolio> existing = portfolioRepository.findByUserId(order.getUserId())
         *        .stream()
         *        .filter(p -> p.getListingId().equals(order.getListing().getId()))
         *        .findFirst();
         *
         * 2. Ako postoji (BUY — dodaj kolicinu, azuriraj prosecnu cenu):
         *    Portfolio portfolio = existing.get();
         *    int oldQty = portfolio.getQuantity();
         *    BigDecimal oldAvg = portfolio.getAverageBuyPrice();
         *
         *    Ako quantity > 0 (BUY):
         *      int newQty = oldQty + quantity;
         *      BigDecimal newAvg = (oldAvg.multiply(BigDecimal.valueOf(oldQty))
         *          .add(price.multiply(BigDecimal.valueOf(quantity))))
         *          .divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);
         *      portfolio.setQuantity(newQty);
         *      portfolio.setAverageBuyPrice(newAvg);
         *
         *    Ako quantity < 0 (SELL):
         *      int newQty = oldQty + quantity;  // quantity je negativan
         *      portfolio.setQuantity(newQty);
         *      // Prosecna cena ostaje ista kod prodaje
         *      Ako newQty <= 0: portfolioRepository.delete(portfolio); return;
         *
         *    portfolioRepository.save(portfolio);
         *
         * 3. Ako ne postoji i quantity > 0 (prvi BUY):
         *    Portfolio portfolio = new Portfolio();
         *    portfolio.setUserId(order.getUserId());
         *    portfolio.setListingId(order.getListing().getId());
         *    portfolio.setListingTicker(order.getListing().getTicker());
         *    portfolio.setListingName(order.getListing().getName());
         *    portfolio.setListingType(order.getListing().getListingType().name());
         *    portfolio.setQuantity(quantity);
         *    portfolio.setAverageBuyPrice(price);
         *    portfolio.setPublicQuantity(0);
         *    portfolioRepository.save(portfolio);
         *
         * 4. Ako ne postoji i quantity < 0 (SELL bez poseda):
         *    Ovo ne bi trebalo da se desi jer FundsVerificationService
         *    proverava da korisnik ima dovoljno hartija pri kreiranju naloga.
         *    Baciti IllegalStateException ako se ipak desi.
         *
         * 5. Edge cases:
         *    - Kolicina moze biti 0 nakon SELL-a — obrisati portfolio entry
         *    - publicQuantity se NE menja pri trgovini (samo korisnik to podesava za OTC)
         *    - lastModified se automatski azurira (@PrePersist/@PreUpdate u Portfolio modelu)
         */
        log.info("TODO: implementirati"); // stub
    }

    /**
     * Azurira stanje na racunu korisnika i uplacuje proviziju na racun banke.
     *
     * @param order      nalog koji se izvrsava
     * @param quantity   kolicina hartija u ovom fill-u
     * @param price      cena po jedinici pri izvrsavanju
     * @param commission izracunata provizija za ovaj fill
     */
    private void updateAccountBalance(Order order, int quantity, BigDecimal price, BigDecimal commission) {
        /*
         * TODO: Implementirati azuriranje stanja na racunu
         *
         * 1. Dohvatiti Account korisnika:
         *    Account userAccount = accountRepository.findById(order.getAccountId())
         *        .orElseThrow(() -> ...);
         *
         * 2. Izracunati ukupan iznos:
         *    BigDecimal totalAmount = price
         *        .multiply(BigDecimal.valueOf(quantity))
         *        .multiply(BigDecimal.valueOf(order.getContractSize()))
         *        .setScale(4, RoundingMode.HALF_UP);
         *
         * 3. Za BUY nalog:
         *    - Skinuti sa racuna korisnika: totalAmount + commission
         *    userAccount.setBalance(userAccount.getBalance().subtract(totalAmount).subtract(commission));
         *    userAccount.setAvailableBalance(userAccount.getAvailableBalance().subtract(totalAmount).subtract(commission));
         *
         * 4. Za SELL nalog:
         *    - Dodati na racun korisnika: totalAmount - commission
         *    userAccount.setBalance(userAccount.getBalance().add(totalAmount).subtract(commission));
         *    userAccount.setAvailableBalance(userAccount.getAvailableBalance().add(totalAmount).subtract(commission));
         *
         * 5. Sacuvati korisnikov racun:
         *    accountRepository.save(userAccount);
         *
         * 6. Uplatiti proviziju na racun banke:
         *    - Pronaci racun banke (potrebno definisati kako se identifikuje — po accountNumber ili posebna tabela)
         *    - Dodati commission na balance bankinog racuna
         *    - Sacuvati racun banke
         *    - Kreirati Transaction zapis za proviziju
         *
         * 7. Edge cases:
         *    - Stanje na racunu moze postati negativno ako se cena promenila od odobrenja
         *      Razmotriti da li baciti exception ili dozvoliti (overdraft)
         *    - Commission ide UVEK na racun banke, bez obzira da li je BUY ili SELL
         *    - Za zaposlene (order.getUserRole() == "EMPLOYEE") koji trguju u ime banke:
         *      provizija se NE naplacuje (skida se sa bankinog racuna bez komisije)
         *    - availableBalance mora ostati >= 0
         */
        log.info("TODO: implementirati"); // stub
    }

    /**
     * Racuna proviziju za dati iznos i tip naloga.
     *
     * Specifikacija:
     * - MARKET: max(14% * price, $7)
     * - LIMIT:  max(24% * price, $12)
     *
     * @param totalPrice ukupna cena fill-a (price * quantity * contractSize)
     * @param orderType  tip naloga (MARKET ili LIMIT)
     * @return izracunata provizija
     */
    private BigDecimal calculateCommission(BigDecimal totalPrice, OrderType orderType) {
        /*
         * TODO: Implementirati racunanje provizije
         *
         * Za MARKET (i bivse STOP naloge koji su postali MARKET):
         *   return totalPrice.multiply(MARKET_COMMISSION_RATE).max(MARKET_COMMISSION_MIN);
         *
         * Za LIMIT (i bivse STOP_LIMIT naloge koji su postali LIMIT):
         *   return totalPrice.multiply(LIMIT_COMMISSION_RATE).max(LIMIT_COMMISSION_MIN);
         *
         * Napomena: STOP i STOP_LIMIT se ovde ne pojavljuju jer ih je
         * StopOrderActivationService vec pretvorio u MARKET/LIMIT.
         * Ipak, za sigurnost, handle ih kao MARKET/LIMIT respektivno.
         */
        // TODO: implementirati racunanje provizije
        log.debug("calculateCommission - returning ZERO (stub)");
        return BigDecimal.ZERO;
    }
}
