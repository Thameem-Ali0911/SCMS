package com.scms.service;

import com.scms.repository.LoginThrottleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * LoginThrottleCleanupJob — periodically removes stale, no-longer-locked
 * throttle rows so the `login_throttle` table never grows unbounded.
 *
 * Runs every hour and only ever deletes rows whose window has closed AND
 * whose lock (if any) has already expired — it can never undo an active
 * lockout early. This replaces the v1.3 LoginAttemptService.cache.clear()
 * behaviour, which wiped the ENTIRE in-memory cache (including active
 * lockouts) once it reached 10,000 entries — a self-inflicted denial of
 * service an attacker could trigger on purpose.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginThrottleCleanupJob {

    private final LoginThrottleRepository loginThrottleRepository;

    @Scheduled(fixedRate = 60 * 60 * 1000) // hourly
    @Transactional
    public void cleanupStaleEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        int removed = loginThrottleRepository.deleteStaleEntries(cutoff, LocalDateTime.now());
        if (removed > 0) {
            log.debug("LoginThrottle cleanup removed {} stale entries", removed);
        }
    }
}
