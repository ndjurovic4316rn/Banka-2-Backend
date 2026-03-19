package rs.raf.banka2_bek.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import rs.raf.banka2_bek.payment.model.PaymentCode;

import java.math.BigDecimal;

@Data
public class CreatePaymentRequestDto {

    @NotBlank(message = "Source account is required")
    @Length(min = 10, max = 20, message = "Account number must be between 10 and 20 characters")
    private String fromAccount;

    @NotBlank(message = "Destination account is required")
    @Length(min = 10, max = 20, message = "Account number must be between 10 and 20 characters")
    private String toAccount;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Payment code is required")
    private PaymentCode paymentCode;

    private String referenceNumber;

    @NotBlank(message = "Description is required")
    private String description;
}

