package com.scms.service;

import com.scms.model.LoginThrottle;
import com.scms.model.User;
import com.scms.repository.LoginThrottleRepository;
import com.scms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * LoginAttemptService — brute-force protection, now correctly persisted.
 *
 * CHANGE in v2.0 (production hardening) — replaces the v1.3 in-memory
 * ConcurrentHashMap entirely. Two independent layers, both DB-backed (no
 * Redis required — see LoginThrottle for the scaling tradeoff discussion):
 *
 *   1. ACCOUNT-level lockout (per email) — recordFailure()/recordSuccess()
 *      write directly to User.failedLoginAttempts / User.accountLockedUntil.
 *      Spring Security's UserDetails.isAccountNonLocked() reads this same
 *      field, so the lock is enforced by the framework itself, not by a
 *      side-channel pre-check that something could bypass (the v1.3
 *      Authentication finding this fixes).
 *
 *   2. IP-level throttle (independent of which email was tried) — protects
 *      against credential stuffing, where an attacker rotates through many
 *      different email addresses from one source IP. Backed by the
 *      `login_throttle` table, so it is correctly shared across every
 *      application instance behind a load balancer — fixing the v1.3
 *      Scalability finding ("two instances behind a load balancer = zero
 *      rate limiting").
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final LoginThrottleRepository loginThrottleRepository;

    /** @return true if this IP has exceeded the throttle and should be blocked BEFORE we even check credentials. */
    @Transactional
    public boolean isIpThrottled(String ip) {
        LoginThrottle throttle = loginThrottleRepository.findById(ip).orElse(null);
        if (throttle == null) return false;
        return throttle.getLockedUntil() != null && LocalDateTime.now().isBefore(throttle.getLockedUntil());
    }

    @Transactional
    public void recordIpAttempt(String ip) {
        LocalDateTime now = LocalDateTime.now();
        LoginThrottle throttle = loginThrottleRepository.findById(ip).orElse(null);

        if (throttle == null || throttle.getWindowStart().isBefore(now.minusMinutes(15))) {
            throttle = LoginThrottle.builder()
                    .ip(ip)
                    .attemptCount(1)
                    .windowStart(now)
                    .lockedUntil(null)
                    .build();
        } else {
            throttle.setAttemptCount(throttle.getAttemptCount() + 1);
            if (throttle.getAttemptCount() >= 20) { // generous — this is anti credential-stuffing, not per-account lockout
                throttle.setLockedUntil(now.plusMinutes(15));
                log.warn("IP {} throttled after {} login attempts in 15 minutes", ip, throttle.getAttemptCount());
            }
        }
        loginThrottleRepository.save(throttle);
    }

    /** Account-level: called after a failed password check for a known email. */
    @Transactional
    public void recordFailure(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= User.MAX_FAILED_ATTEMPTS) {
            user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(User.LOCKOUT_MINUTES));
            log.warn("Account {} locked for {} minutes after {} failed attempts",
                    user.getEmail(), User.LOCKOUT_MINUTES, attempts);
        }
        userRepository.save(user);
    }

    @Transactional
    public void recordSuccess(User user) {
        if (user.getFailedLoginAttempts() != 0 || user.getAccountLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setAccountLockedUntil(null);
            userRepository.save(user);
        }
    }

    public int remainingAttempts(User user) {
        return Math.max(0, User.MAX_FAILED_ATTEMPTS - user.getFailedLoginAttempts());
    }
}
