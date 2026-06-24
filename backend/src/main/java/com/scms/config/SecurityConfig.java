package com.scms.config;

import com.scms.common.HttpRequestUtils;
import com.scms.security.JwtAuthFilter;
import com.scms.security.RequestIdFilter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * SecurityConfig — the heart of SCMS's authentication/authorization posture.
 *
 * CHANGE in v2.0 (production hardening):
 *
 *   • @EnableMethodSecurity + URL-level role rules. v1.3 finding: "SecurityConfig
 *     grants all authenticated requests to all endpoints — no URL-level role
 *     enforcement, only method-level [manual requireAdmin() checks]". Now
 *     /api/admin/** requires ROLE_ADMIN at the HTTP layer itself, and the
 *     STAFF-specific queue/assignment endpoints in ComplaintController carry
 *     their own @PreAuthorize("hasAnyRole('STAFF','ADMIN')") — a future
 *     developer adding a new admin controller method gets URL-level
 *     protection automatically by virtue of the path prefix, instead of
 *     having to remember to call requireAdmin() by hand in every method body
 *     (the old failure mode: forget the call once, ship an unauthenticated
 *     admin endpoint).
 *
 *   • Conditional HSTS. v1.3 finding: "HSTS is commented out — runs over
 *     HTTP with zero transport security in production." HSTS is now a real
 *     header, gated by `security.hsts.enabled` (default true) so it can be
 *     turned off only for local HTTP development, never silently shipped off
 *     in a real deployment.
 *
 *   • Trusted-proxy configuration is wired into HttpRequestUtils here at
 *     startup from `security.trusted-proxies` — see HttpRequestUtils for the
 *     X-Forwarded-For spoofing fix this enables.
 *
 *   • CSRF remains disabled for the stateless Bearer-token API surface (a
 *     forged cross-site request cannot attach an Authorization header, so
 *     CSRF does not apply to it). The ONE cookie this API issues — the
 *     refresh-token cookie — is scoped to path /api/auth, marked HttpOnly +
 *     SameSite=Strict, and the refresh endpoint only ever issues new tokens
 *     (it cannot modify data), which is the documented threat-model
 *     rationale the v1.3 report asked for ("No CSRF protection documentation
 *     explaining the threat model to future maintainers").
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter     jwtAuthFilter;
    private final RequestIdFilter   requestIdFilter;
    private final UserDetailsService userDetailsService;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${security.hsts.enabled:true}")
    private boolean hstsEnabled;

    @Value("${security.trusted-proxies:}")
    private String trustedProxies;

    @PostConstruct
    public void configureTrustedProxies() {
        HttpRequestUtils.configureTrustedProxies(trustedProxies);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost factor 12 — ~250ms per hash on typical hardware. High enough to
        // resist offline brute force, low enough not to bottleneck login
        // throughput. (Documented here per the v1.3 MENTOR NOTE this preserves.)
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        // Required for the refresh-token HttpOnly cookie to be sent cross-origin
        // when the frontend and backend are deployed on different domains.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/categories/**").authenticated() // read access for the complaint form; writes are @PreAuthorize-gated in the controller
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .headers(headers -> {
                headers.frameOptions(frame -> frame.deny());
                headers.contentTypeOptions(contentTypeOptions -> {});
                headers.referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                headers.addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy",
                        "default-src 'self'; frame-ancestors 'none'; object-src 'none'"));
                if (hstsEnabled) {
                    headers.httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31536000));
                }
            })
            .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(jwtAuthFilter, RequestIdFilter.class);

        return http.build();
    }
}
