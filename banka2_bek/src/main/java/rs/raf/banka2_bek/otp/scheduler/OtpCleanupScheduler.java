package rs.raf.banka2_bek.otp.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.otp.repository.OtpVerificationRepository;

import java.time.LocalDateTime;

/**
 * Scheduler za ciscenje isteklih i upotrebljenih OTP zapisa.
 * Pokrece se svaki dan u 04:00 ujutru.
 * Brise OTP zapise starije od 24h i koriscene starije od 1h.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpCleanupScheduler {

    private final OtpVerificationRepository otpVerificationRepository;

    /**
     * Dnevno ciscenje OTP tabele — pokrece se u 04:00 ujutru.
     * <p>
     * Cron format: sekunda minut sat dan-u-mesecu mesec dan-u-nedelji
     * "0 0 4 * * *" = 04:00:00 svakog dana
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanupExpiredOtps() {
        log.info("Starting OTP cleanup...");

        LocalDateTime expiredCutoff = LocalDateTime.now().minusHours(24);
        int deletedExpired = otpVerificationRepository.deleteAllOlderThan(expiredCutoff);
        log.info("Deleted {} expired OTP records (>24h).", deletedExpired);

        LocalDateTime usedCutoff = LocalDateTime.now().minusHours(1);
        int deletedUsed = otpVerificationRepository.deleteUsedOlderThan(usedCutoff);
        log.info("Deleted {} used OTP records (>1h).", deletedUsed);

        log.info("OTP cleanup completed.");
    }
}
