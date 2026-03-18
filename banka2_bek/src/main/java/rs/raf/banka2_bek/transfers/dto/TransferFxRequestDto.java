package rs.raf.banka2_bek.transfers.dto;

import java.math.BigDecimal;

public class TransferFxRequestDto {
    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;

    public TransferFxRequestDto() {
    }

    public String getFromAccountNumber() { return fromAccountNumber; }

    public void setFromAccountNumber(String fromAccountNumber) {
        this.fromAccountNumber = fromAccountNumber;
    }

    public String getToAccountNumber() { return toAccountNumber; }

    public void setToAccountNumber(String toAccountNumber) {
        this.toAccountNumber = toAccountNumber;
    }

    public BigDecimal getAmount() { return amount; }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
