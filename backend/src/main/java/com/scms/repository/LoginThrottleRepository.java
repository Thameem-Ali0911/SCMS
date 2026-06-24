package com.scms.repository;

import com.scms.model.LoginThrottle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LoginThrottleRepository extends JpaRepository<LoginThrottle, String> {

    /**
     * Deletes throttle rows whose lock has long expired and which have not
     * been touched recently. Used by a scheduled cleanup job so the table
     * never grows unbounded.
     *
     * MENTOR NOTE — replacing the v1.3 "cache.clear() on capacity" bug:
     * The old LoginAttemptService wiped its ENTIRE in-memory cache once it
     * hit 10,000 entries — which an attacker could trigger deliberately by
     * spamming 10,001 failed logins from throwaway IPs, instantly un-locking
     * every currently-locked attacker at once (a denial-of-service against
     * the rate limiter itself). This scheduled job only ever removes rows
     * that are already stale (window closed, no lock in effect), so it
     * cannot be abused to clear an active lockout early.
     */
    @Modifying
    @Query("DELETE FROM LoginThrottle t WHERE t.windowStart < :cutoff " +
           "AND (t.lockedUntil IS NULL OR t.lockedUntil < :now)")
    int deleteStaleEntries(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);
}
