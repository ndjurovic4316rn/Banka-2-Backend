package rs.raf.banka2_bek.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePaymentRecipientRequestDto {

    @NotBlank(message = "Recipient name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Account number is required")
    @Size(min = 18, max = 18, message = "Account number must be exactly 18 digits")
    @Pattern(regexp = "\\d{18}", message = "Account number must contain only digits")
    private String accountNumber;
}
