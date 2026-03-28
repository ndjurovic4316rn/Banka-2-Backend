package rs.raf.banka2_bek.berza.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.berza.dto.ExchangeDto;
import rs.raf.banka2_bek.berza.model.Exchange;
import rs.raf.banka2_bek.berza.repository.ExchangeRepository;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Servis za upravljanje berzama i proveru radnog vremena.
 *
 * Specifikacija: Celina 3 - Berza
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeManagementService {

    private final ExchangeRepository exchangeRepository;

    /**
     * Proverava da li je berza trenutno otvorena.
     * TODO: Dodati holiday calendar proveru u buducnosti.
     */
    public boolean isExchangeOpen(String acronym) {
        Exchange exchange = exchangeRepository.findByAcronym(acronym)
                .orElseThrow(() -> new RuntimeException("Exchange not found: " + acronym));
        if (exchange.isTestMode()) {
            return true;
        }
        // TODO: Implement full weekday + holiday check
        return true;
    }

    /**
     * Vraca listu svih aktivnih berzi sa computed poljima.
     */
    public List<ExchangeDto> getAllExchanges() {
        return exchangeRepository.findByActiveTrue().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Vraca detalje jedne berze po skracenici.
     */
    public ExchangeDto getByAcronym(String acronym) {
        Exchange exchange = exchangeRepository.findByAcronym(acronym)
                .orElseThrow(() -> new RuntimeException("Exchange not found: " + acronym));
        return toDto(exchange);
    }

    /**
     * Ukljucuje/iskljucuje test mode za berzu.
     */
    @Transactional
    public void setTestMode(String acronym, boolean enabled) {
        Exchange exchange = exchangeRepository.findByAcronym(acronym)
                .orElseThrow(() -> new RuntimeException("Exchange not found: " + acronym));
        exchange.setTestMode(enabled);
        exchangeRepository.save(exchange);
        log.info("Test mode for exchange {} set to {}", acronym, enabled);
    }

    /**
     * Proverava da li je berza u after-hours periodu.
     * TODO: Implement full after-hours logic.
     */
    public boolean isAfterHours(String acronym) {
        // TODO: Implement after-hours check using postMarketCloseTime
        return false;
    }

    // ── Helper metode ───────────────────────────────────────────────────────────

    private ExchangeDto toDto(Exchange exchange) {
        boolean open = isExchangeOpen(exchange.getAcronym());
        String currentLocalTime;
        try {
            currentLocalTime = ZonedDateTime.now(ZoneId.of(exchange.getTimeZone()))
                    .toLocalTime().toString();
        } catch (Exception e) {
            currentLocalTime = LocalTime.now().toString();
        }

        return ExchangeDto.builder()
                .id(exchange.getId())
                .name(exchange.getName())
                .acronym(exchange.getAcronym())
                .micCode(exchange.getMicCode())
                .country(exchange.getCountry())
                .currency(exchange.getCurrency())
                .timeZone(exchange.getTimeZone())
                .openTime(exchange.getOpenTime() != null ? exchange.getOpenTime().toString() : null)
                .closeTime(exchange.getCloseTime() != null ? exchange.getCloseTime().toString() : null)
                .preMarketOpenTime(exchange.getPreMarketOpenTime() != null ? exchange.getPreMarketOpenTime().toString() : null)
                .postMarketCloseTime(exchange.getPostMarketCloseTime() != null ? exchange.getPostMarketCloseTime().toString() : null)
                .testMode(exchange.isTestMode())
                .active(exchange.isActive())
                .isCurrentlyOpen(open)
                .currentLocalTime(currentLocalTime)
                .nextOpenTime(null) // TODO: Calculate next open time when closed
                .build();
    }
}
