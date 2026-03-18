package rs.raf.banka2_bek.transfers.dto;

import rs.raf.banka2_bek.payment.model.PaymentStatus;

import java.math.BigDecimal;
public class TransferResponseDto {
    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private BigDecimal exchangeRate;
    private BigDecimal commission;
    private String clientFirstName;
    private String clientLastName;
    private PaymentStatus status;

    public TransferResponseDto() {
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


    public BigDecimal getExchangeRate() { return exchangeRate; }

    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }


    public BigDecimal getCommission() { return commission; }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }


    public String getClientFirstName() { return clientFirstName; }

    public void setClientFirstName(String clientFirstName) {
        this.clientFirstName = clientFirstName;
    }


    public String getClientLastName() { return clientLastName; }

    public void setClientLastName(String clientLastName) {
        this.clientLastName = clientLastName;
    }

    public PaymentStatus getStatus() { return status; }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }
}
