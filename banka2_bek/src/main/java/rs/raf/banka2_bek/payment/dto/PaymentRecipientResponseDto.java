package rs.raf.banka2_bek.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PaymentRecipientResponseDto {
    private Long id;
    private String name;
    private String accountNumber;
    private LocalDateTime createdAt;
}
