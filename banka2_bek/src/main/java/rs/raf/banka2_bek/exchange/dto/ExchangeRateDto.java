package rs.raf.banka2_bek.exchange.dto;

public class ExchangeRateDto {

    private String currency;
    private double rate;

    public ExchangeRateDto() {}

    public ExchangeRateDto(String currency, double rate) {
        this.currency = currency;
        this.rate = rate;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }
}