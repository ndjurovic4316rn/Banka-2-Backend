package rs.raf.banka2_bek.option.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Servis za izracunavanje cene opcija koristeci Black-Scholes model.
 *
 * Black-Scholes formula je standardni matematicki model za odredjivanje
 * teorijske cene evropskih opcija (call i put).
 *
 * FORMULA:
 * ========
 *
 * Pomocne promenljive:
 *   d1 = [ln(S/K) + (r + sigma^2 / 2) * T] / (sigma * sqrt(T))
 *   d2 = d1 - sigma * sqrt(T)
 *
 * Call cena:
 *   C = S * N(d1) - K * e^(-r*T) * N(d2)
 *
 * Put cena:
 *   P = K * e^(-r*T) * N(-d2) - S * N(-d1)
 *
 * Gde je:
 *   S     = trenutna cena akcije (spot price)
 *   K     = strike cena opcije
 *   T     = vreme do isteka u godinama (daysToExpiry / 365.0)
 *   r     = risk-free kamatna stopa (koristiti 0.05 = 5% kao default)
 *   sigma = implied volatility (npr. 0.25 = 25%)
 *   N(x)  = kumulativna funkcija normalne distribucije (CDF)
 *   ln    = prirodni logaritam
 *   e     = Ojlerova konstanta (Math.E)
 *
 * IMPLEMENTACIONE NAPOMENE:
 * =========================
 * - Koristiti Math.log() za ln, Math.exp() za e^x, Math.sqrt() za sqrt
 * - Za N(x) implementirati normalCDF() metodu (videti dole)
 * - Sve racunanje raditi u double, konvertovati u BigDecimal na kraju
 * - Ako je T <= 0 (opcija istekla), cena je max(0, intrinsicValue):
 *     CALL intrinsic = max(0, S - K)
 *     PUT intrinsic  = max(0, K - S)
 * - Ako je sigma <= 0, baciti IllegalArgumentException
 * - Rezultat zaokruziti na 4 decimale (scale = 4, HALF_UP)
 *
 * RISK-FREE RATE:
 * ===============
 * Podrazumevana vrednost: 0.05 (5% godisnje)
 * Moze se konfigurisati preko application.properties:
 *   option.risk-free-rate=0.05
 * Koristiti @Value("${option.risk-free-rate:0.05}") za injectovanje.
 */
@Service
public class BlackScholesService {

    /**
     * TODO: Inject iz application.properties sa default vrednoscu 0.05
     * @Value("${option.risk-free-rate:0.05}")
     */
    private static final double DEFAULT_RISK_FREE_RATE = 0.05;

    /**
     * TODO: Izracunava cenu CALL opcije koristeci Black-Scholes formulu.
     *
     * Implementacija:
     *   1. Proveriti da su S > 0, K > 0, sigma > 0
     *   2. Ako je T <= 0, vratiti intrinsic value: max(0, S - K)
     *   3. Izracunati d1 = [ln(S/K) + (r + sigma^2/2) * T] / (sigma * sqrt(T))
     *   4. Izracunati d2 = d1 - sigma * sqrt(T)
     *   5. Vratiti: S * N(d1) - K * e^(-r*T) * N(d2)
     *   6. Zaokruziti na 4 decimale
     *
     * @param spotPrice         S - trenutna cena akcije
     * @param strikePrice       K - strike cena opcije
     * @param timeToExpiryYears T - vreme do isteka u godinama
     * @param riskFreeRate      r - risk-free kamatna stopa
     * @param volatility        sigma - implied volatility
     * @return cena CALL opcije kao BigDecimal
     */
    public BigDecimal calculateCallPrice(double spotPrice, double strikePrice,
                                         double timeToExpiryYears, double riskFreeRate,
                                         double volatility) {
        if (volatility <= 0) throw new IllegalArgumentException("Volatility must be > 0");
        if (timeToExpiryYears <= 0) {
            return BigDecimal.valueOf(Math.max(0, spotPrice - strikePrice))
                    .setScale(4, java.math.RoundingMode.HALF_UP);
        }

        double d1 = (Math.log(spotPrice / strikePrice)
                + (riskFreeRate + volatility * volatility / 2.0) * timeToExpiryYears)
                / (volatility * Math.sqrt(timeToExpiryYears));
        double d2 = d1 - volatility * Math.sqrt(timeToExpiryYears);

        double callPrice = spotPrice * normalCDF(d1)
                - strikePrice * Math.exp(-riskFreeRate * timeToExpiryYears) * normalCDF(d2);

        return BigDecimal.valueOf(callPrice).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * TODO: Izracunava cenu PUT opcije koristeci Black-Scholes formulu.
     *
     * Implementacija:
     *   1. Proveriti da su S > 0, K > 0, sigma > 0
     *   2. Ako je T <= 0, vratiti intrinsic value: max(0, K - S)
     *   3. Izracunati d1 i d2 (isto kao za CALL)
     *   4. Vratiti: K * e^(-r*T) * N(-d2) - S * N(-d1)
     *   5. Zaokruziti na 4 decimale
     *
     * ALTERNATIVA: Moze se koristiti put-call parity:
     *   P = C - S + K * e^(-r*T)
     *   gde je C vec izracunata call cena.
     *
     * @param spotPrice         S - trenutna cena akcije
     * @param strikePrice       K - strike cena opcije
     * @param timeToExpiryYears T - vreme do isteka u godinama
     * @param riskFreeRate      r - risk-free kamatna stopa
     * @param volatility        sigma - implied volatility
     * @return cena PUT opcije kao BigDecimal
     */
    public BigDecimal calculatePutPrice(double spotPrice, double strikePrice,
                                        double timeToExpiryYears, double riskFreeRate,
                                        double volatility) {
        if (volatility <= 0) throw new IllegalArgumentException("Volatility must be > 0");
        if (timeToExpiryYears <= 0) {
            return BigDecimal.valueOf(Math.max(0, strikePrice - spotPrice))
                    .setScale(4, java.math.RoundingMode.HALF_UP);
        }

        double d1 = (Math.log(spotPrice / strikePrice)
                + (riskFreeRate + volatility * volatility / 2.0) * timeToExpiryYears)
                / (volatility * Math.sqrt(timeToExpiryYears));
        double d2 = d1 - volatility * Math.sqrt(timeToExpiryYears);

        double putPrice = strikePrice * Math.exp(-riskFreeRate * timeToExpiryYears) * normalCDF(-d2)
                - spotPrice * normalCDF(-d1);

        return BigDecimal.valueOf(putPrice).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * TODO: Convenience metoda sa default risk-free rate-om.
     * Poziva calculateCallPrice sa DEFAULT_RISK_FREE_RATE.
     */
    public BigDecimal calculateCallPrice(double spotPrice, double strikePrice,
                                         double timeToExpiryYears, double volatility) {
        return calculateCallPrice(spotPrice, strikePrice, timeToExpiryYears,
                DEFAULT_RISK_FREE_RATE, volatility);
    }

    /**
     * TODO: Convenience metoda sa default risk-free rate-om.
     * Poziva calculatePutPrice sa DEFAULT_RISK_FREE_RATE.
     */
    public BigDecimal calculatePutPrice(double spotPrice, double strikePrice,
                                        double timeToExpiryYears, double volatility) {
        return calculatePutPrice(spotPrice, strikePrice, timeToExpiryYears,
                DEFAULT_RISK_FREE_RATE, volatility);
    }

    /**
     * TODO: Kumulativna funkcija normalne distribucije (CDF) - N(x).
     *
     * Vraca verovatnocu da standardna normalna slucajna promenljiva
     * uzme vrednost manju ili jednaku x.
     *
     * OPCIJA 1 (preporuceno): Koristiti Apache Commons Math
     *   - Dodati dependency: org.apache.commons:commons-math3:3.6.1
     *   - new NormalDistribution().cumulativeProbability(x)
     *
     * OPCIJA 2: Horner-ova aproksimacija (bez eksternih biblioteka)
     *   Abramowitz & Stegun aproksimacija (greska < 1.5e-7):
     *
     *   if (x < 0) return 1 - normalCDF(-x);
     *
     *   double t = 1.0 / (1.0 + 0.2316419 * x);
     *   double d = 0.3989422804014327;  // 1/sqrt(2*PI)
     *   double probability = d * Math.exp(-x * x / 2.0)
     *       * t * (0.3193815
     *       + t * (-0.3565638
     *       + t * (1.7814779
     *       + t * (-1.8212560
     *       + t * 1.3302744))));
     *   return 1.0 - probability;
     *
     * OPCIJA 3: Java built-in (Java 17+)
     *   Nema direktnu CDF, ali moze se koristiti:
     *   0.5 * (1 + org.apache.commons.math3.special.Erf.erf(x / Math.sqrt(2)))
     *
     * @param x vrednost za koju se racuna CDF
     * @return N(x) - verovatnoca P(Z <= x) za standardnu normalnu distribuciju
     */
    protected double normalCDF(double x) {
        // Horner approximation (Abramowitz & Stegun, error < 1.5e-7)
        if (x < 0) return 1.0 - normalCDF(-x);
        double t = 1.0 / (1.0 + 0.2316419 * x);
        double d = 0.3989422804014327; // 1/sqrt(2*PI)
        double prob = d * Math.exp(-x * x / 2.0)
                * t * (0.3193815 + t * (-0.3565638 + t * (1.7814779
                + t * (-1.8212560 + t * 1.3302744))));
        return 1.0 - prob;
    }
}
