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
 * MENTOR NOTE — WHY implement UserDetails? Spring Security's authentication
 * pipeline calls loadUserByUsername() on your UserDetailsService, which must
 * return a UserDetails object. By making User implement UserDetails directly,
 * we skip writing an adapter class. Spring Security can then call
 * getPassword(), getAuthorities(), isEnabled() directly on our User object —
 * clean and zero-overhead.
 *
 * Lombok annotations:
 *
 * @Data → generates getters, setters, equals, hashCode, toString
 * @Builder → User.builder().email("x").password("h").build()
 * @NoArgsConstructor → required by JPA (it instantiates entities with no-arg
 * constructor)
 * @AllArgsConstructor → required by @Builder when combined with
 * @NoArgsConstructor
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    /**
     * Email is the login identifier (username). Must be unique.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /**
     * Stored as a BCrypt hash — NEVER plain text. Named "password" to satisfy
     * the UserDetails contract (getPassword()).
     */
    @Column(name = "password_hash", nullable = false)
    private String password;

    @Column(length = 20)
    private String phone;

    @Builder.Default
    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Roles — Many-to-Many with the `roles` table via `user_roles` join table.
     * EAGER fetch: roles are always loaded with the user in one query. In
     * Spring Boot this is safe because the User object only lives for the
     * duration of one HTTP request (stateless JWT — no persistent session).
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
    /**
     * Spring Security uses this to check passwords during login.
     */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * Our "username" is the email address.
     */
    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Converts Role entities to Spring Security GrantedAuthority objects.
     * "ADMIN" in DB → "ROLE_ADMIN" authority → .hasRole("ADMIN") works.
     */
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

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    // ─── Helpers ───────────────────────────────────────────────────────────
    public String getFullName() {
        return firstName + " " + lastName;
    }
}
