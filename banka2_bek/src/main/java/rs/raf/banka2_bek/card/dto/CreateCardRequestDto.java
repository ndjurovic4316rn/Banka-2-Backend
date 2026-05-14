package rs.raf.banka2_bek.card.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import rs.raf.banka2_bek.card.model.CardCategory;
import rs.raf.banka2_bek.card.model.CardType;

import java.math.BigDecimal;

@Data
public class CreateCardRequestDto {
    @NotNull(message = "Account ID je obavezan")
    private Long accountId;
    private BigDecimal cardLimit;
    private CardType cardType;
    /** Kategorija: DEBIT (default) / CREDIT / INTERNET_PREPAID. */
    private CardCategory cardCategory;
    /** Za CREDIT: maksimalni kredit limit (banka odobrava). Ignorise se za ostale. */
    private BigDecimal creditLimit;
}
