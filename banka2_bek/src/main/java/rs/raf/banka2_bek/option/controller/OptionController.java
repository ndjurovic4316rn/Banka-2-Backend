package rs.raf.banka2_bek.option.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.auth.dto.MessageResponseDto;
import rs.raf.banka2_bek.option.dto.OptionChainDto;
import rs.raf.banka2_bek.option.dto.OptionDto;
import rs.raf.banka2_bek.option.service.OptionGeneratorService;
import rs.raf.banka2_bek.option.service.OptionService;

import java.util.List;

/**
 * REST kontroler za opcije.
 *
 * Endpointi:
 *   GET  /options?stockListingId={id}  - option chain za akciju
 *   GET  /options/{id}                 - detalji jedne opcije
 *   POST /options/{id}/exercise        - izvrsavanje opcije (samo aktuar)
 *   POST /options/generate             - triggerovanje generisanja opcija (samo admin)
 *
 * TODO: Autorizacija:
 *   - GET endpointi su dostupni svim autentifikovanim korisnicima
 *   - POST /options/{id}/exercise zahteva ulogu ACTUARY (aktuar)
 *   - POST /options/generate zahteva ulogu ADMIN
 *   - Koristiti @PreAuthorize anotacije sa Spring Security izrazima
 *
 * TODO: Error handling:
 *   - EntityNotFoundException → 404
 *   - IllegalStateException → 400 (npr. opcija istekla, nije ITM)
 *   - AccessDeniedException → 403
 *   - Globalni exception handler bi trebalo da hvata ove izuzetke
 *     (proveriti da li vec postoji u auth/config paketu)
 */
@Tag(name = "Options", description = "Opcije — option chain, detalji, izvrsavanje i generisanje")
@RestController
@RequestMapping("/options")
@RequiredArgsConstructor
public class OptionController {

    private final OptionService optionService;
    private final OptionGeneratorService optionGeneratorService;

    /**
     * TODO: Vraca option chain za odredjenu akciju, grupisan po settlement datumu.
     *
     * Implementacija:
     *   1. Validirati da je stockListingId prosledjen (required = true)
     *   2. Pozvati optionService.getOptionsForStock(stockListingId)
     *   3. Vratiti 200 OK sa listom OptionChainDto
     *
     * Odgovor sadrzi listu objekata, svaki sa:
     *   - settlementDate
     *   - calls (List<OptionDto>) sortirane po strike ceni
     *   - puts (List<OptionDto>) sortirane po strike ceni
     *   - currentStockPrice
     *
     * @param stockListingId ID Listing entiteta (akcije)
     * @return lista OptionChainDto
     */
    @Operation(summary = "Vraca option chain za akciju",
               description = "Vraca sve opcije za datu akciju, grupisane po settlement datumu. "
                           + "Za svaki datum prikazuje CALL i PUT opcije sortirane po strike ceni.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Option chain uspesno vracen"),
            @ApiResponse(responseCode = "404", description = "Akcija sa datim ID-jem ne postoji",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class)))
    })
    @GetMapping
    public ResponseEntity<List<OptionChainDto>> getOptionsForStock(
            @RequestParam Long stockListingId) {
        return ResponseEntity.ok(optionService.getOptionsForStock(stockListingId));
    }

    /**
     * TODO: Vraca detalje jedne opcije po ID-ju.
     *
     * @param id ID opcije
     * @return OptionDto sa svim detaljima
     */
    @Operation(summary = "Vraca detalje jedne opcije",
               description = "Vraca sve podatke o opciji ukljucujuci trenutnu cenu akcije i ITM status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Opcija uspesno pronadjena"),
            @ApiResponse(responseCode = "404", description = "Opcija sa datim ID-jem ne postoji",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<OptionDto> getOptionById(@PathVariable Long id) {
        return ResponseEntity.ok(optionService.getOptionById(id));
    }

    /**
     * TODO: Izvrsava (exercise) opciju.
     *
     * Implementacija:
     *   1. Izvuci email korisnika iz Authentication objekta:
     *      authentication.getName() vraca email (sub claim iz JWT-a)
     *   2. Pozvati optionService.exerciseOption(id, userEmail)
     *   3. Ako uspe, vratiti 200 OK sa porukom
     *
     * AUTORIZACIJA: Samo korisnici sa ulogom ACTUARY mogu izvrsavati opcije.
     * Koristiti @PreAuthorize("hasRole('ACTUARY')") ili ekvivalent.
     *
     * @param id             ID opcije za izvrsavanje
     * @param authentication Spring Security authentication objekat
     * @return 200 OK sa porukom o uspesnom izvrsavanju
     */
    @Operation(summary = "Izvrsi opciju",
               description = "Izvrsava opciju (exercise). Samo aktuari mogu izvrsavati opcije. "
                           + "CALL: kupovina akcija po strike ceni. PUT: prodaja akcija po strike ceni. "
                           + "Opcija mora biti in-the-money i ne sme biti istekla.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Opcija uspesno izvrsena"),
            @ApiResponse(responseCode = "400", description = "Opcija nije validna za izvrsavanje (istekla ili nije ITM)",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Korisnik nema ulogu ACTUARY",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Opcija ne postoji",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class)))
    })
    @PostMapping("/{id}/exercise")
    @PreAuthorize("hasRole('ACTUARY')")
    public ResponseEntity<MessageResponseDto> exerciseOption(
            @PathVariable Long id, Authentication authentication) {
        String userEmail = authentication.getName();
        optionService.exerciseOption(id, userEmail);
        return ResponseEntity.ok(new MessageResponseDto("Opcija uspesno izvrsena."));
    }

    /**
     * TODO: Triggeruje generisanje opcija za sve STOCK listinge.
     *
     * Poziva OptionGeneratorService.generateAllOptions() koji:
     *   1. Pronalazi sve STOCK listinge
     *   2. Za svaki generise CALL i PUT opcije
     *   3. Koristi Black-Scholes za izracunavanje cena
     *
     * AUTORIZACIJA: Samo ADMIN moze triggerovati generisanje.
     *
     * NAPOMENA: Ovo se takodje poziva automatski iz OptionScheduler-a svakodnevno,
     * ali ovaj endpoint omogucava rucno triggerovanje po potrebi.
     *
     * @return 200 OK sa porukom o uspesnom generisanju
     */
    @Operation(summary = "Generiši opcije za sve akcije",
               description = "Rucno triggeruje generisanje opcija za sve STOCK listinge. "
                           + "Generisu se CALL i PUT opcije za vise strike cena i settlement datuma. "
                           + "Cene se racunaju Black-Scholes modelom. Samo za administratore.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Opcije uspesno generisane"),
            @ApiResponse(responseCode = "403", description = "Korisnik nema ulogu ADMIN",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class)))
    })
    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponseDto> generateOptions() {
        optionGeneratorService.generateAllOptions();
        return ResponseEntity.ok(new MessageResponseDto("Opcije uspesno generisane za sve akcije."));
    }
}
