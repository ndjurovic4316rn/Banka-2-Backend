package rs.raf.banka2_bek.otp.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.otp.repository.OtpVerificationRepository;

/**
 * Scheduler za ciscenje isteklih i upotrebljenih OTP zapisa.
 *
 * Pokrece se svaki dan u 04:00 ujutru.
 * Brise:
 *   - Sve OTP zapise starije od 24 sata (istekli, nikad korisceni)
 *   - Sve koriscene (used=true) OTP zapise starije od 1 sata
 *
 * Svrha: Sprecava nepotrebno gomilanje OTP zapisa u bazi
 *         i odrzava performanse tabele otp_verifications.
 *
 * TODO: Implementirati logiku:
 *   1. Logirati pocetak ciscenja: "Pokrecem ciscenje OTP zapisa..."
 *   2. Obrisati sve zapise gde:
 *      - createdAt < (now - 24h)
 *      Koristiti custom @Query u OtpVerificationRepository:
 *        @Modifying
 *        @Query("DELETE FROM OtpVerification o WHERE o.createdAt < :cutoff")
 *        int deleteAllOlderThan(@Param("cutoff") LocalDateTime cutoff);
 *   3. Logirati: "Obrisano {} starih OTP zapisa (>24h)."
 *   4. Obrisati sve koriscene zapise gde:
 *      - used = true AND createdAt < (now - 1h)
 *      Koristiti custom @Query:
 *        @Modifying
 *        @Query("DELETE FROM OtpVerification o WHERE o.used = true AND o.createdAt < :cutoff")
 *        int deleteUsedOlderThan(@Param("cutoff") LocalDateTime cutoff);
 *   5. Logirati: "Obrisano {} koriscenih OTP zapisa (>1h)."
 *   6. Logirati zavrsetak: "Ciscenje OTP zapisa zavrseno."
 *
 * NAPOMENA: Potrebno je dodati gore navedene @Query metode u OtpVerificationRepository.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpCleanupScheduler {

    private final OtpVerificationRepository otpVerificationRepository;

    /**
     * Dnevno ciscenje OTP tabele — pokrece se u 04:00 ujutru.
     *
     * Cron format: sekunda minut sat dan-u-mesecu mesec dan-u-nedelji
     * "0 0 4 * * *" = 04:00:00 svakog dana
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanupExpiredOtps() {
        // TODO: Implementirati prema uputstvima iznad
        // LocalDateTime expiredCutoff = LocalDateTime.now().minusHours(24);
        // int deletedExpired = otpVerificationRepository.deleteAllOlderThan(expiredCutoff);
        // log.info("Obrisano {} starih OTP zapisa (>24h).", deletedExpired);
        //
        // LocalDateTime usedCutoff = LocalDateTime.now().minusHours(1);
        // int deletedUsed = otpVerificationRepository.deleteUsedOlderThan(usedCutoff);
        // log.info("Obrisano {} koriscenih OTP zapisa (>1h).", deletedUsed);
        log.info("OTP cleanup - TODO: implementirati");
    }
}
