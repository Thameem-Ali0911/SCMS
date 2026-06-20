package com.scms.service;

import com.scms.dto.AuthDtos.*;
import com.scms.model.Role;
import com.scms.model.User;
import com.scms.repository.RoleRepository;
import com.scms.repository.UserRepository;
import com.scms.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
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
 * AuthService — register and login business logic.
 *
 * CHANGE in v1.3 (Production hardening):
 *   Login now enforces brute-force protection via LoginAttemptService:
 *     1. Check if this email+IP is currently locked → 429 Too Many Requests
 *     2. On failed auth → record failure (increment counter)
 *     3. On success    → record success (reset counter)
 *     4. Warn the user how many attempts remain before lockout
 *
 *   HttpServletRequest is injected to extract the real client IP.
 *   We check X-Forwarded-For first (populated by reverse proxies like Nginx)
 *   then fall back to getRemoteAddr() for direct connections.
 *
 * MENTOR NOTE — why IP extraction is complex:
 * In production, your backend sits behind an Nginx or AWS ALB reverse proxy.
 * The proxy receives the client's real IP and forwards the request to Spring Boot.
 * request.getRemoteAddr() returns the PROXY's IP (e.g. 10.0.0.1), not the client's.
 * The real IP is in the X-Forwarded-For header. We check that first.
 *
 * For localhost dev: getRemoteAddr() correctly returns 127.0.0.1.
 * So this works correctly in both environments.
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

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException(
                        "USER role not seeded. Check DataSeeder."));

        String hash = passwordEncoder.encode(req.getPassword());

        User user = User.builder()
                .firstName(req.getFirstName().trim())
                .lastName(req.getLastName().trim())
                .email(req.getEmail().toLowerCase().trim())
                .password(hash)
                .phone(req.getPhone())
                .active(true)
                .roles(Set.of(userRole))
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        String token = jwtUtil.generateToken(user.getEmail());
        return buildResponse(user, token);
    }

    /**
     * Login with brute-force protection.
     *
     * @param req     login credentials
     * @param request HTTP request for IP extraction
     */
    public AuthResponse login(LoginRequest req, HttpServletRequest request) {
        String email = req.getEmail().toLowerCase().trim();
        String ip    = extractClientIp(request);

        // ── Step 1: check lockout BEFORE attempting authentication ────────
        // This prevents the attacker from even triggering an auth check.
        if (loginAttemptService.isBlocked(email, ip)) {
            long mins = loginAttemptService.minutesRemaining(email, ip);
            throw new TooManyAttemptsException(
                    "Too many failed login attempts. Please wait "
                    + Math.max(1, mins) + " minute(s) before trying again.");
        }

        // ── Step 2: attempt authentication ────────────────────────────────
        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, req.getPassword()));

            User user = (User) auth.getPrincipal();

            // ── Step 3: success — reset counter ───────────────────────────
            loginAttemptService.recordSuccess(email, ip);
            log.info("User logged in: {} from {}", user.getEmail(), ip);

            String token = jwtUtil.generateToken(user.getEmail());
            return buildResponse(user, token);

        } catch (BadCredentialsException | DisabledException ex) {
            // ── Step 4: failure — record and warn ─────────────────────────
            if (ex instanceof BadCredentialsException) {
                loginAttemptService.recordFailure(email, ip);
                int failures  = loginAttemptService.failureCount(email, ip);
                int remaining = LoginAttemptService.MAX_ATTEMPTS - failures;

                if (remaining <= 0) {
                    throw new TooManyAttemptsException(
                            "Account temporarily locked after too many failed attempts. "
                            + "Try again in " + LoginAttemptService.LOCKOUT_MINUTES + " minutes.");
                }

                // Throw a richer message so the frontend can show remaining attempts
                throw new BadCredentialsException(
                        "Incorrect email or password. "
                        + remaining + " attempt(s) remaining before lockout.");
            }
            // DisabledException — propagate as-is (handled by GlobalExceptionHandler)
            throw ex;
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For can be a comma-separated list; first entry is the client
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private AuthResponse buildResponse(User user, String token) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .roles(roleNames)
                .build();
    }

    /**
     * Custom exception for rate limiting — maps to HTTP 429 in GlobalExceptionHandler.
     * A static nested class because it's only meaningful in auth context.
     */
    public static class TooManyAttemptsException extends RuntimeException {
        public TooManyAttemptsException(String msg) { super(msg); }
    }
}
