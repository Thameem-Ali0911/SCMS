package com.scms.config;

import com.scms.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * SecurityConfig — central Spring Security configuration.
 *
 * CHANGE in v1.3 (Production hardening) — Security Headers added:
 *
 *   X-Frame-Options: DENY
 *     Prevents the app from being embedded in an <iframe>. Protects against
 *     clickjacking attacks where an attacker overlays a hidden iframe on top
 *     of a legitimate page to steal clicks.
 *
 *   Content-Security-Policy (CSP):
 *     Tells the browser which sources of scripts, styles, images etc. are
 *     trusted. Any script not from an allowed source is blocked. This is the
 *     primary defence against XSS (Cross-Site Scripting) attacks.
 *     For a React SPA loaded from the same origin: default-src 'self'.
 *     We allow 'unsafe-inline' for styles because Vite injects them at dev time.
 *     In production with a proper build pipeline, remove 'unsafe-inline'.
 *
 *   Strict-Transport-Security (HSTS):
 *     Tells browsers to ONLY connect via HTTPS for the next year.
 *     Prevents SSL-stripping man-in-the-middle attacks.
 *     Only effective when your server actually has HTTPS. Uncomment for production.
 *
 *   X-Content-Type-Options: nosniff
 *     Prevents browsers from MIME-sniffing a response away from the declared
 *     Content-Type. Stops an attacker from tricking the browser into treating
 *     a JSON response as executable script.
 *
 *   Referrer-Policy: strict-origin-when-cross-origin
 *     Controls how much URL information is included in the Referer header.
 *     This prevents the full path (which may contain sensitive IDs) from being
 *     leaked to third-party analytics or CDN providers.
 *
 * MENTOR NOTE — Spring Security headers API:
 * http.headers() gives you a fluent API for every standard security header.
 * Spring adds X-Content-Type-Options and X-Frame-Options by default;
 * we're overriding and extending them. CSP is not added by default — you must
 * configure it explicitly.
 *
 * MENTOR NOTE — CSRF still disabled:
 * CSRF attacks exploit the browser automatically attaching cookies to requests.
 * Since we use JWT in the Authorization header (not a cookie), CSRF doesn't apply.
 * If you later switch to HttpOnly cookies, CSRF protection becomes necessary again.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter      jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── CORS ──────────────────────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsSource()))

            // ── CSRF (disabled — JWT in headers, not cookies) ─────────────
            .csrf(AbstractHttpConfigurer::disable)

            // ── Security headers ──────────────────────────────────────────
            .headers(headers -> headers
                // Prevent clickjacking
                .frameOptions(frame -> frame.deny())

                // Prevent MIME sniffing
                .contentTypeOptions(cto -> {})  // adds nosniff by default

                // CSP — restrict resource loading to same origin
                // MENTOR NOTE: In dev, 'unsafe-inline' is needed for Vite HMR.
                // In production build, Vite hashes inline styles — you can tighten this.
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data:; " +
                    "font-src 'self'; " +
                    "connect-src 'self'; " +
                    "frame-ancestors 'none'"
                ))

                // Referrer leakage control
                .referrerPolicy(rp -> rp.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))

                // HSTS — uncomment when you have HTTPS configured
                // .httpStrictTransportSecurity(hsts -> hsts
                //     .maxAgeInSeconds(31536000)   // 1 year
                //     .includeSubDomains(true))
            )

            // ── Route permissions ──────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()  // login + register: public
                .anyRequest().authenticated()                 // all other routes: JWT required
            )

            // ── Stateless sessions (JWT — no HttpSession) ──────────────────
            .sessionManagement(s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Auth provider + JWT filter ─────────────────────────────────
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", cfg);
        return src;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider();
        p.setUserDetailsService(userDetailsService);
        p.setPasswordEncoder(passwordEncoder());
        return p;
    }

    /**
     * BCrypt cost factor 12 = 2^12 ≈ 4096 rounds.
     * Slow enough to frustrate brute-force, imperceptible to a user logging in.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
