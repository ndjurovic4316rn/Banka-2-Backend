package rs.raf.banka2_bek.payment.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum PaymentCode {
    CODE_220("220"),
    CODE_221("221"),
    CODE_222("222"),
    CODE_223("223"),
    CODE_224("224"),
    CODE_225("225"),
    CODE_226("226"),
    CODE_227("227"),
    CODE_228("228"),
    CODE_231("231"),
    CODE_240("240"),
    CODE_241("241"),
    CODE_242("242"),
    CODE_244("244"),
    CODE_245("245"),
    CODE_246("246"),
    CODE_247("247"),
    CODE_248("248"),
    CODE_249("249"),
    CODE_253("253"),
    CODE_254("254"),
    CODE_257("257"),
    CODE_258("258"),
    CODE_260("260"),
    CODE_261("261"),
    CODE_262("262"),
    CODE_263("263"),
    CODE_264("264"),
    CODE_265("265"),
    CODE_266("266"),
    CODE_270("270"),
    CODE_271("271"),
    CODE_272("272"),
    CODE_273("273"),
    CODE_275("275"),
    CODE_276("276"),
    CODE_277("277"),
    CODE_278("278"),
    CODE_279("279"),
    CODE_280("280"),
    CODE_281("281"),
    CODE_282("282"),
    CODE_283("283"),
    CODE_284("284"),
    CODE_285("285"),
    CODE_286("286"),
    CODE_287("287"),
    CODE_288("288"),
    CODE_289("289"),
    CODE_290("290");

    private static final Set<String> ALLOWED_CODES = Arrays.stream(values())
            .map(PaymentCode::getCode)
            .collect(Collectors.toUnmodifiableSet());

    private final String code;

    PaymentCode(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static boolean isSupported(String code) {
        if (code == null) {
            return false;
        }
        return ALLOWED_CODES.contains(code.trim());
    }

    @JsonCreator
    public static PaymentCode fromCode(String code) {
        String normalized = code == null ? null : code.trim();
        return Arrays.stream(values())
                .filter(value -> value.code.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid payment code."));
    }
}
