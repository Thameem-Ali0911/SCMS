package com.scms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LoginAttemptService — in-memory brute-force protection.
 *
 * MENTOR NOTE — why in-memory?
 * For a college project, in-memory is perfectly fine. Data resets on restart,
 * which is acceptable because a restarted server implicitly clears any transient
 * attack state. In production (multiple server instances, persistent lockout
 * across restarts), you'd store this in Redis with a TTL.
 *
 * How it works:
 *   - On every FAILED login: increment counter for key = email:ip
 *   - On every SUCCESSFUL login: reset counter for that key
 *   - If counter >= MAX_ATTEMPTS: block for LOCKOUT_MINUTES minutes
 *   - After LOCKOUT_MINUTES elapses: counter resets automatically on next attempt
 *
 * Key format: "email::ip" — rate-limits per (email + IP) pair.
 * This prevents:
 *   1. An attacker hammering one email from one IP (brute force)
 *   2. An attacker rotating emails from one IP (credential stuffing)
 * It does NOT prevent distributed attacks (different IPs attacking one email).
 * For that, you'd need per-email limits too — that's the next enhancement.
 *
 * MENTOR NOTE — ConcurrentHashMap:
 * HTTP requests are handled by multiple threads concurrently. A regular HashMap
 * is not thread-safe — concurrent reads/writes corrupt it. ConcurrentHashMap
 * uses lock striping so multiple threads can read/write safely without blocking
 * each other the way a synchronized(this) block would.
 *
 * MENTOR NOTE — Why not @Cacheable?
 * Spring's @Cacheable (with Caffeine/Ehcache) is the production upgrade path —
 * it gives you TTL, eviction, size limits, metrics. For now ConcurrentHashMap
 * is readable and teaches the concept without adding dependencies.
 */
@Service
@Slf4j
public class LoginAttemptService {

    public  static final int MAX_ATTEMPTS      = 5;
    public  static final int LOCKOUT_MINUTES   = 15;
    private static final int MAX_CACHE_ENTRIES = 10_000;  // prevent memory leak

    private record AttemptRecord(int count, LocalDateTime lockedUntil) {}

    private final Map<String, AttemptRecord> cache = new ConcurrentHashMap<>();

    /**
     * Called by AuthService on FAILED login attempt.
     * @param email the email address that was attempted
     * @param ip    the client's IP address
     */
    public void recordFailure(String email, String ip) {
        String key = buildKey(email, ip);

        // Evict oldest entry if cache is full (safety valve)
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
            log.warn("LoginAttemptService cache cleared — max capacity reached");
        }

        cache.compute(key, (k, existing) -> {
            // Ignore new failures while still locked (don't reset the timer)
            if (existing != null && isLocked(existing)) return existing;

            int newCount = (existing == null) ? 1 : existing.count() + 1;
            LocalDateTime lockUntil = null;

            if (newCount >= MAX_ATTEMPTS) {
                lockUntil = LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES);
                log.warn("Account locked for {} from IP {} after {} failed attempts. "
                        + "Lock expires at {}", email, ip, newCount, lockUntil);
            }
            return new AttemptRecord(newCount, lockUntil);
        });
    }

    /**
     * Called by AuthService on SUCCESSFUL login.
     * Resets the counter so a legitimate user who had previous failures can log in normally.
     */
    public void recordSuccess(String email, String ip) {
        cache.remove(buildKey(email, ip));
    }

    /**
     * Returns true if this email + IP combination is currently locked out.
     * AuthService calls this before attempting authentication.
     */
    public boolean isBlocked(String email, String ip) {
        AttemptRecord record = cache.get(buildKey(email, ip));
        if (record == null) return false;

        if (isLocked(record)) return true;

        // Lock window has expired — clean up and allow
        if (record.lockedUntil() != null
                && LocalDateTime.now().isAfter(record.lockedUntil())) {
            cache.remove(buildKey(email, ip));
            return false;
        }
        return false;
    }

    /**
     * Returns how many minutes remain on the lockout (for error messages).
     */
    public long minutesRemaining(String email, String ip) {
        AttemptRecord record = cache.get(buildKey(email, ip));
        if (record == null || record.lockedUntil() == null) return 0;
        return java.time.temporal.ChronoUnit.MINUTES.between(
                LocalDateTime.now(), record.lockedUntil());
    }

    /**
     * Returns the current failure count (used in error messages to warn the user).
     */
    public int failureCount(String email, String ip) {
        AttemptRecord record = cache.get(buildKey(email, ip));
        return record == null ? 0 : record.count();
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private boolean isLocked(AttemptRecord r) {
        return r.lockedUntil() != null
                && LocalDateTime.now().isBefore(r.lockedUntil());
    }

    private String buildKey(String email, String ip) {
        return email.toLowerCase() + "::" + ip;
    }
}
