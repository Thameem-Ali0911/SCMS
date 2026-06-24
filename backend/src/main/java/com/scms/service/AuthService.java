package com.scms.service;

import com.scms.dto.AuthDtos.*;
import com.scms.model.Role;
import com.scms.model.User;
import com.scms.repository.RoleRepository;
import com.scms.repository.UserRepository;
import com.scms.security.JwtUtil;
import com.scms.common.HttpRequestUtils;
import com.scms.common.Roles;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * AuthService — register, login, refresh, and logout business logic.
 *
 * CHANGE in v2.0 (production hardening):
 *
 *   • login() now issues an ACCESS + REFRESH token pair (see JwtUtil) instead
 *     of one 24-hour token. AuthResult carries the refresh token separately
 *     from the JSON-serialisable AuthResponse so AuthController can put it
 *     directly into an HttpOnly cookie — it is never serialised into a
 *     response body a script could read.
 *
 *   • Brute-force protection is now two independent, DB-backed layers (see
 *     LoginAttemptService for the full rationale): an IP-level throttle
 *     checked BEFORE we even look at credentials, and an account-level
 *     lockout enforced by Spring Security itself via
 *     UserDetails.isAccountNonLocked().
 *
 *   • refresh() validates a refresh token and issues a new access token
 *     (rotating the refresh token too, for defense in depth against replay).
 *
 *   • logout() bumps User.tokenVersion, which immediately invalidates every
 *     access AND refresh token previously issued to that user — see
 *     JwtUtil / JwtAuthFilter.
 *
 *   • IP extraction now goes through HttpRequestUtils, which only trusts
 *     X-Forwarded-For when the request actually came from a configured
 *     trusted reverse proxy — closing the v1.3 spoofing vulnerability.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository        userRepository;
    private final RoleRepository        roleRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtUtil               jwtUtil;
    private final AuthenticationManager authManager;
    private final LoginAttemptService   loginAttemptService;

    @Getter
    @Builder
    public static class AuthResult {
        private final AuthResponse body;
        private final String       refreshToken;
        private final long         refreshExpiresInSeconds;
    }

    @Transactional
    public AuthResult register(RegisterRequest req) {
        String email = req.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        Role userRole = roleRepository.findByName(Roles.USER)
                .orElseThrow(() -> new IllegalStateException(
                        "USER role not seeded. Check Flyway reference-data migration."));

        String hash = passwordEncoder.encode(req.getPassword());

        User user = User.builder()
                .firstName(req.getFirstName().trim())
                .lastName(req.getLastName().trim())
                .email(email)
                .password(hash)
                .phone(req.getPhone())
                .active(true)
                .roles(Set.of(userRole))
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        return issueTokenPair(user);
    }

    /** Login with two-layer brute-force protection (see class javadoc). */
    public AuthResult login(LoginRequest req) {
        String email = req.getEmail().toLowerCase().trim();
        String ip    = HttpRequestUtils.currentIp();

        if (loginAttemptService.isIpThrottled(ip)) {
            throw new TooManyAttemptsException(
                    "Too many login attempts from this network. Please wait a few minutes before trying again.");
        }
        loginAttemptService.recordIpAttempt(ip);

        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, req.getPassword()));

            User user = (User) auth.getPrincipal();
            loginAttemptService.recordSuccess(user);
            log.info("User logged in: {} from {}", user.getEmail(), ip);

            return issueTokenPair(user);

        } catch (LockedException ex) {
            throw new TooManyAttemptsException(
                    "Account temporarily locked after too many failed attempts. "
                    + "Try again in a few minutes.");
        } catch (DisabledException ex) {
            throw ex; // handled by GlobalExceptionHandler -> 403 Account Disabled
        } catch (BadCredentialsException ex) {
            userRepository.findByEmail(email).ifPresent(loginAttemptService::recordFailure);
            int remaining = userRepository.findByEmail(email)
                    .map(loginAttemptService::remainingAttempts)
                    .orElse(User.MAX_FAILED_ATTEMPTS);

            if (remaining <= 0) {
                throw new TooManyAttemptsException(
                        "Account temporarily locked after too many failed attempts. "
                        + "Try again in " + User.LOCKOUT_MINUTES + " minutes.");
            }
            throw new BadCredentialsException(
                    "Incorrect email or password. " + remaining + " attempt(s) remaining before lockout.");
        }
    }

    /** Validates an incoming refresh token and issues a fresh access+refresh pair (rotation). */
    @Transactional
    public AuthResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException("No refresh token provided.");
        }
        String email;
        try {
            email = jwtUtil.extractEmail(refreshToken);
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid refresh token.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token."));

        boolean valid = jwtUtil.isValid(refreshToken, email, user.getTokenVersion(), JwtUtil.TYPE_REFRESH);
        if (!valid || !user.isEnabled() || !user.isAccountNonLocked()) {
            throw new BadCredentialsException("Refresh token is invalid or expired. Please log in again.");
        }

        return issueTokenPair(user);
    }

    /** Bumps tokenVersion — every previously issued access/refresh token for this user stops working immediately. */
    @Transactional
    public void logout(User user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        log.info("User logged out (tokens revoked): {}", user.getEmail());
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private AuthResult issueTokenPair(User user) {
        String accessToken  = jwtUtil.generateAccessToken(user.getEmail(), user.getTokenVersion());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), user.getTokenVersion());

        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        AuthResponse body = AuthResponse.builder()
                .accessToken(accessToken)
                .expiresInSeconds(jwtUtil.getAccessExpirationMs() / 1000)
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .roles(roleNames)
                .build();

        return AuthResult.builder()
                .body(body)
                .refreshToken(refreshToken)
                .refreshExpiresInSeconds(jwtUtil.getRefreshExpirationMs() / 1000)
                .build();
    }

    /** Maps to HTTP 429 in GlobalExceptionHandler. */
    public static class TooManyAttemptsException extends RuntimeException {
        public TooManyAttemptsException(String msg) { super(msg); }
    }
}
