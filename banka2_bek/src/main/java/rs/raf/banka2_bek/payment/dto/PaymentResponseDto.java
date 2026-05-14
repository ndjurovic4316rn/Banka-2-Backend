package rs.raf.banka2_bek.payment.dto;

import lombok.Builder;
import lombok.Value;
import rs.raf.banka2_bek.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class PaymentResponseDto {
    Long id;
    String orderNumber;
    String fromAccount;
    String toAccount;
    BigDecimal amount;
    BigDecimal fee;
    String currency;
    String paymentCode;
    String referenceNumber;
    String description;
    String recipientName;
    PaymentDirection direction;
    PaymentStatus status;
    LocalDateTime createdAt;
    String sagaPhase;
}
