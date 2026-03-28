package rs.raf.banka2_bek.option.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.option.dto.OptionChainDto;
import rs.raf.banka2_bek.option.dto.OptionDto;
import rs.raf.banka2_bek.option.mapper.OptionMapper;
import rs.raf.banka2_bek.option.model.Option;
import rs.raf.banka2_bek.option.model.OptionType;
import rs.raf.banka2_bek.option.repository.OptionRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Glavni servis za rad sa opcijama.
 *
 * Pruza CRUD operacije, option chain grupisanje i logiku za izvrsavanje (exercise) opcija.
 *
 * TODO: Za exerciseOption() potrebno je integrisati sa:
 *   - Portfolio/Account servisima (provera sredstava, azuriranje portfolija)
 *   - Transaction servisom (kreiranje zapisa o transakciji)
 *   - Ovi servisi jos nisu implementirani u Celina 3, pa koristiti placeholder
 */
@Service
@RequiredArgsConstructor
public class OptionService {

    private static final Logger log = LoggerFactory.getLogger(OptionService.class);

    private final OptionRepository optionRepository;
    private final ListingRepository listingRepository;

    /**
     * TODO: Vraca option chain za odredjenu akciju, grupisan po settlement datumu.
     *
     * Implementacija:
     *   1. Proveriti da listing sa datim ID-jem postoji u ListingRepository
     *      - Ako ne postoji, baciti EntityNotFoundException
     *   2. Ucitati sve opcije za taj listing: optionRepository.findByStockListingId(listingId)
     *   3. Grupisati opcije po settlementDate: Collectors.groupingBy(Option::getSettlementDate)
     *   4. Za svaku grupu:
     *      a. Razdvojiti na calls (optionType == CALL) i puts (optionType == PUT)
     *      b. Sortirati calls i puts po strikePrice ascending
     *      c. Mapirati u OptionDto putem OptionMapper
     *      d. Kreirati OptionChainDto sa settlementDate, calls, puts, currentStockPrice
     *   5. Sortirati rezultat po settlementDate ascending (najblizi datum prvi)
     *   6. Vratiti listu OptionChainDto
     *
     * @param listingId ID Listing entiteta (akcije)
     * @return lista OptionChainDto grupivisana po settlement datumu
     * @throws EntityNotFoundException ako listing ne postoji
     */
    public List<OptionChainDto> getOptionsForStock(Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new EntityNotFoundException("Listing id: " + listingId + " not found."));

        List<Option> options = optionRepository.findByStockListingId(listingId);
        BigDecimal currentPrice = listing.getPrice();

        Map<LocalDate, List<Option>> grouped = options.stream()
                .collect(Collectors.groupingBy(Option::getSettlementDate));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    OptionChainDto chain = new OptionChainDto();
                    chain.setSettlementDate(entry.getKey());
                    chain.setCurrentStockPrice(currentPrice);

                    List<OptionDto> calls = entry.getValue().stream()
                            .filter(o -> o.getOptionType() == OptionType.CALL)
                            .sorted(Comparator.comparing(Option::getStrikePrice))
                            .map(o -> OptionMapper.toDto(o, currentPrice))
                            .toList();
                    chain.setCalls(calls);

                    List<OptionDto> puts = entry.getValue().stream()
                            .filter(o -> o.getOptionType() == OptionType.PUT)
                            .sorted(Comparator.comparing(Option::getStrikePrice))
                            .map(o -> OptionMapper.toDto(o, currentPrice))
                            .toList();
                    chain.setPuts(puts);

                    return chain;
                })
                .toList();
    }

    /**
     * TODO: Vraca detalje jedne opcije po ID-ju.
     *
     * Implementacija:
     *   1. Ucitati opciju iz optionRepository.findById(optionId)
     *      - Ako ne postoji, baciti EntityNotFoundException
     *   2. Ucitati trenutnu cenu akcije iz option.getStockListing().getPrice()
     *   3. Mapirati u OptionDto putem OptionMapper.toDto(option, currentPrice)
     *   4. Vratiti DTO
     *
     * @param optionId ID opcije
     * @return OptionDto sa svim detaljima
     * @throws EntityNotFoundException ako opcija ne postoji
     */
    public OptionDto getOptionById(Long optionId) {
        Option option = optionRepository.findById(optionId)
                .orElseThrow(() -> new EntityNotFoundException("Option id: " + optionId + " not found."));

        BigDecimal currentPrice = option.getStockListing().getPrice();
        return OptionMapper.toDto(option, currentPrice);
    }

    /**
     * TODO: Izvrsava (exercise) opciju — kupac koristi pravo iz opcije.
     *
     * PRAVILA:
     * ========
     * 1. AUTORIZACIJA: Samo korisnici sa ulogom ACTUARY (aktuar) mogu izvrsavati opcije.
     *    - Proveriti rolu iz SecurityContext-a ili prosledjenog email-a.
     *    - Ako korisnik nije aktuar, baciti AccessDeniedException ili IllegalStateException.
     *
     * 2. VALIDACIJA:
     *    - Opcija mora postojati (EntityNotFoundException ako ne postoji)
     *    - Opcija mora biti validna (settlementDate >= danas)
     *      Ako je istekla, baciti IllegalStateException("Opcija je istekla")
     *    - Opcija mora biti "in the money":
     *      CALL: currentStockPrice > strikePrice
     *      PUT:  currentStockPrice < strikePrice
     *      Ako nije ITM, baciti IllegalStateException("Opcija nije in-the-money")
     *
     * 3. CALL EXERCISE LOGIKA:
     *    - Kupac kupuje contractSize akcija po strikePrice
     *    - Ukupan trosak: strikePrice * contractSize
     *    - Proveriti da kupac ima dovoljno sredstava na racunu
     *    - Skinuti sredstva sa racuna kupca
     *    - Dodati akcije u portfolio kupca
     *    - Kreirati transakciju (buy)
     *
     * 4. PUT EXERCISE LOGIKA:
     *    - Kupac prodaje contractSize akcija po strikePrice
     *    - Proveriti da kupac ima dovoljno akcija u portfoliju
     *    - Ukloniti akcije iz portfolija kupca
     *    - Dodati sredstva na racun kupca: strikePrice * contractSize
     *    - Kreirati transakciju (sell)
     *
     * 5. POSLE IZVRSAVANJA:
     *    - Smanjiti openInterest na opciji za 1
     *    - Sacuvati promenu
     *    - Loguj izvrsavanje
     *
     * NAPOMENA: Integracija sa Account/Portfolio/Transaction servisima
     * ce biti implementirana kada ti servisi budu dostupni u Celini 3/4.
     *
     * @param optionId  ID opcije za izvrsavanje
     * @param userEmail email korisnika koji izvrsava opciju
     * @throws EntityNotFoundException ako opcija ne postoji
     * @throws IllegalStateException   ako opcija nije validna za izvrsavanje
     */
    @Transactional
    public void exerciseOption(Long optionId, String userEmail) {
        Option option = optionRepository.findById(optionId)
                .orElseThrow(() -> new EntityNotFoundException("Option id: " + optionId + " not found."));

        // Provera isteka
        if (option.getSettlementDate().isBefore(LocalDate.now())) {
            throw new IllegalStateException("Opcija je istekla (settlement: " + option.getSettlementDate() + ")");
        }

        BigDecimal currentPrice = option.getStockListing().getPrice();
        BigDecimal strike = option.getStrikePrice();

        // Provera ITM
        if (option.getOptionType() == OptionType.CALL && currentPrice.compareTo(strike) <= 0) {
            throw new IllegalStateException("CALL opcija nije in-the-money (stock: " + currentPrice + ", strike: " + strike + ")");
        }
        if (option.getOptionType() == OptionType.PUT && currentPrice.compareTo(strike) >= 0) {
            throw new IllegalStateException("PUT opcija nije in-the-money (stock: " + currentPrice + ", strike: " + strike + ")");
        }

        // TODO: Integracija sa Account/Portfolio servisima
        //   - CALL: proveriti sredstva, kupiti akcije po strike ceni
        //   - PUT: proveriti portfolio, prodati akcije po strike ceni

        option.setOpenInterest(option.getOpenInterest() - 1);
        optionRepository.save(option);

        log.info("Opcija {} izvrsena od strane {}", option.getTicker(), userEmail);
    }
}
