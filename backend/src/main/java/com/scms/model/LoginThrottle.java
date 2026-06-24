package com.scms.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * LoginThrottle — IP-level sliding-window throttle for the login/register
 * endpoints, persisted to the database.
 *
 * MENTOR NOTE — replacing the in-memory ConcurrentHashMap (v1.3 finding):
 * The v1.3 LoginAttemptService stored attempt counters in a JVM
 * ConcurrentHashMap. That looks like it works (and does, on one instance)
 * but is a correctness bug, not just a performance one:
 *   - Restart the app  → every lockout silently vanishes.
 *   - Run 2+ instances  → an attacker round-robins between them and the
 *     rate limit never triggers, because each instance has its own map.
 *
 * Storing the counter here means every application instance sees the same
 * state via the shared MySQL database — correct behaviour with zero new
 * infrastructure (no Redis required). For very high login volume (thousands
 * of attempts/sec) a Redis-backed counter with TTL would have lower latency;
 * that tradeoff is documented in README.md's "Scaling beyond this design"
 * section as a deliberate, revisitable choice rather than an oversight.
 *
 * This table throttles by IP ADDRESS ALONE (not email+IP) specifically to
 * catch credential-stuffing attacks where an attacker rotates through many
 * different email addresses from one source IP. Per-account lockout
 * (failed attempts against ONE email) is handled separately and correctly
 * via User.failedLoginAttempts / User.accountLockedUntil, which Spring
 * Security's UserDetails.isAccountNonLocked() now actually reads — fixing
 * the v1.3 finding that isAccountNonLocked() always returned true.
 */
@Entity
@Table(name = "login_throttle")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginThrottle {

    @Id
    @Column(length = 64)
    private String ip;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
}
