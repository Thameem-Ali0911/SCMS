package com.scms.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User — JPA entity for the `users` table, also implements UserDetails.
 *
 * CHANGE in v2.0 (production hardening):
 *
 *   • failedLoginAttempts / accountLockedUntil — account-level brute-force
 *     lockout now lives on the entity itself, so isAccountNonLocked() reads
 *     real state instead of hardcoding `true`. v1.3 tracked lockout in a
 *     side-table (an in-memory map) that Spring Security's authentication
 *     pipeline never actually consulted — meaning if anything ever called
 *     AuthenticationManager.authenticate() directly (bypassing AuthService's
 *     manual pre-check), the lockout was silently bypassed. Now the lock is
 *     enforced by the UserDetails contract itself — there is no bypass path.
 *
 *   • tokenVersion — incremented on logout / explicit "sign out everywhere".
 *     Every issued JWT embeds the tokenVersion that was current at issue
 *     time; JwtAuthFilter rejects any token whose embedded version doesn't
 *     match the current DB value. This is how v2.0 supports real token
 *     revocation without maintaining a separate blacklist table of every
 *     token ever issued — see JwtUtil and JwtAuthFilter.
 *
 *   • @Table(indexes = …) on email — v1.3 already had a unique constraint
 *     (which MySQL backs with an index automatically), so this is mostly
 *     documentation of that fact, kept explicit for clarity alongside the
 *     other entities that now declare indexes.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final int LOCKOUT_MINUTES     = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    /** Email is the login identifier (username). Must be unique. */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /** Stored as a BCrypt hash — NEVER plain text. */
    @Column(name = "password_hash", nullable = false)
    private String password;

    @Column(length = 20)
    private String phone;

    @Builder.Default
    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ── Account-level brute-force lockout (v2.0) ───────────────────────────

    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;

    // ── Token revocation (v2.0) ─────────────────────────────────────────────

    /**
     * Incremented whenever the user logs out (or an admin force-revokes their
     * sessions). Every JWT carries the tokenVersion that was valid when it was
     * issued; a mismatch at validation time means "this token was logged out
     * after being issued" → reject it, even though it hasn't expired yet.
     */
    @Builder.Default
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    /**
     * Roles — Many-to-Many with the `roles` table via `user_roles` join table.
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private final Set<Role> roles = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ─── UserDetails interface ─────────────────────────────────────────────

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName()))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * CHANGE in v2.0: now reads real lockout state instead of always
     * returning true. A lock that has expired is treated as unlocked even
     * if accountLockedUntil hasn't been cleared yet — LoginAttemptService
     * clears it lazily on the next login attempt.
     */
    @Override
    public boolean isAccountNonLocked() {
        return accountLockedUntil == null || LocalDateTime.now().isAfter(accountLockedUntil);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    // ─── Role helpers ──────────────────────────────────────────────────────

    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(r -> r.getName().equalsIgnoreCase(roleName));
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
