package rs.raf.banka2_bek.exchange.dto;

public class CalculateExchangeResponseDto {

    private double convertedAmount;
    private double exchangeRate;
    private String fromCurrency;
    private String toCurrency;

    public CalculateExchangeResponseDto() {}

    public CalculateExchangeResponseDto(double convertedAmount, double exchangeRate,
                                        String fromCurrency, String toCurrency) {
        this.convertedAmount = convertedAmount;
        this.exchangeRate = exchangeRate;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
    }


    public double getConvertedAmount() { return convertedAmount; }
    public void setConvertedAmount(double convertedAmount) { this.convertedAmount = convertedAmount; }

    public double getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(double exchangeRate) { this.exchangeRate = exchangeRate; }

    public String getFromCurrency() { return fromCurrency; }
    public void setFromCurrency(String fromCurrency) { this.fromCurrency = fromCurrency; }

    public String getToCurrency() { return toCurrency; }
    public void setToCurrency(String toCurrency) { this.toCurrency = toCurrency; }
}
