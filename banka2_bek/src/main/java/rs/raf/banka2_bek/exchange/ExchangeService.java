package rs.raf.banka2_bek.exchange;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.exchange.dto.CalculateExchangeResponseDto;
import rs.raf.banka2_bek.exchange.dto.ExchangeRateDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ExchangeService {

    private final RestTemplate restTemplate;

    @Value("${exchange.api.key}")
    private String apiKey;

    @Value("${exchange.api.url}")
    private String apiUrl;

    public ExchangeService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<ExchangeRateDto> getAllRates() {

        String url = apiUrl + "?access_key=" + apiKey +
                "&symbols=RSD,EUR,CHF,USD,GBP,JPY,CAD,AUD";

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map<String, Object> body = response.getBody();

        if (body == null || body.get("rates") == null) {
            return new ArrayList<>();
        }

        Map<String, Object> rates = (Map<String, Object>) body.get("rates");

        Double eurToRsd = getDouble(rates.get("RSD"));

        if (eurToRsd == null || eurToRsd == 0.0) {
            throw new RuntimeException("RSD rate not found.");
        }

        String[] currencies = {"RSD", "EUR", "CHF", "USD", "GBP", "JPY", "CAD", "AUD"};

        List<ExchangeRateDto> result = new ArrayList<>();

        for (String currency : currencies) {

            if ("RSD".equals(currency)) {
                result.add(new ExchangeRateDto("RSD", 1.0));
                continue;
            }

            if ("EUR".equals(currency)) {
                double rate = round(1.0 / eurToRsd, 6);
                result.add(new ExchangeRateDto("EUR", rate));
                continue;
            }

            Double eurToTarget = getDouble(rates.get(currency));

            if (eurToTarget != null) {
                double rsdToTarget = eurToTarget / eurToRsd;
                result.add(new ExchangeRateDto(currency, round(rsdToTarget, 6)));
            }
        }

        return result;
    }

    private Double getDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }


    public CalculateExchangeResponseDto calculate(double amount, String toCurrency) {

        if ("RSD".equalsIgnoreCase(toCurrency)) {
            return new CalculateExchangeResponseDto(amount, 1.0, "RSD", "RSD");
        }

        List<ExchangeRateDto> rates = getAllRates();

        double rsdToTarget = rates.stream()
                .filter(r -> r.getCurrency().equalsIgnoreCase(toCurrency))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Currency not supported: " + toCurrency))
                .getRate();

        // Prodajni kurs = +2% na srednji kurs (banka prodaje valutu klijentu)
        double sellRate = round(rsdToTarget * 1.02, 6);

        // Provizija 0.5%
        double commission = 0.005;

        double convertedAmount = round((amount * sellRate) * (1 - commission), 4);

        return new CalculateExchangeResponseDto(convertedAmount, sellRate, "RSD", toCurrency.toUpperCase());
    }


    private double[] convertToRsd(double amount, String fromCurrency, List<ExchangeRateDto> rates) {
        double rsdToFrom = rates.stream()
                .filter(r -> r.getCurrency().equalsIgnoreCase(fromCurrency))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Currency not supported: " + fromCurrency))
                .getRate();

        double sellRate = round((1.0 / rsdToFrom) * 1.02, 6);
        double commission = 0.005;
        double rsdAmount = round((amount * sellRate) * (1 - commission), 4);

        return new double[]{rsdAmount, sellRate};
    }

    public CalculateExchangeResponseDto calculateCross(double amount, String fromCurrency, String toCurrency) {

        if ("RSD".equalsIgnoreCase(fromCurrency)) {
            return calculate(amount, toCurrency);
        }

        List<ExchangeRateDto> rates = getAllRates();
        double[] conversion = convertToRsd(amount, fromCurrency, rates);
        double rsdAmount = conversion[0];
        double sellRate = conversion[1];

        if ("RSD".equalsIgnoreCase(toCurrency)) {
            return new CalculateExchangeResponseDto(rsdAmount, sellRate, fromCurrency.toUpperCase(), "RSD");
        }

        CalculateExchangeResponseDto step2 = calculate(rsdAmount, toCurrency);
        double crossRate = round(step2.getConvertedAmount() / amount, 6);

        return new CalculateExchangeResponseDto(
                step2.getConvertedAmount(),
                crossRate,
                fromCurrency.toUpperCase(),
                toCurrency.toUpperCase()
        );
    }


}