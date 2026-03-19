package rs.raf.banka2_bek.exchange.dto;

import java.time.LocalDate;

public class ExchangeRateDto {

    private String currency;
    private double rate;
    private double buyRate;
    private double sellRate;
    private double middleRate;
    private String date;

    public ExchangeRateDto() {}

    public ExchangeRateDto(String currency, double middleRate) {
        this.currency = currency;
        this.rate = middleRate;
        this.middleRate = middleRate;
        this.buyRate = Math.round(middleRate * 0.98 * 1000000.0) / 1000000.0;
        this.sellRate = Math.round(middleRate * 1.02 * 1000000.0) / 1000000.0;
        this.date = LocalDate.now().toString();
    }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public double getRate() { return rate; }
    public void setRate(double rate) { this.rate = rate; }

    public double getBuyRate() { return buyRate; }
    public void setBuyRate(double buyRate) { this.buyRate = buyRate; }

    public double getSellRate() { return sellRate; }
    public void setSellRate(double sellRate) { this.sellRate = sellRate; }

    public double getMiddleRate() { return middleRate; }
    public void setMiddleRate(double middleRate) { this.middleRate = middleRate; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
}
