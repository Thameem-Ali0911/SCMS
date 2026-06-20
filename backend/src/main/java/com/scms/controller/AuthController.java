package com.scms.controller;

import com.scms.dto.AuthDtos.*;
import com.scms.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — REST endpoints for register and login.
 *
 * CHANGE in v1.3 (Production hardening):
 *   • All @ExceptionHandler methods REMOVED — GlobalExceptionHandler now
 *     handles every exception centrally. Controllers should have ZERO
 *     exception-handling code.
 *   • login() now accepts HttpServletRequest so AuthService can extract
 *     the client IP for brute-force rate limiting.
 *
 * MENTOR NOTE — clean controller principle:
 * A controller's ONLY responsibilities are:
 *   1. Accept the HTTP request and parse its body
 *   2. Call the service
 *   3. Return the HTTP response
 *
 * Authentication, authorization, validation error formatting, business rules —
 * none of these belong in a controller. Compare this version with the original
 * (which had 40+ lines of @ExceptionHandler methods) and notice how much cleaner
 * this is. That code hasn't disappeared — it's now in GlobalExceptionHandler,
 * doing the same job for EVERY controller at once.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** POST /api/auth/register */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(req));
    }

    /**
     * POST /api/auth/login
     *
     * HttpServletRequest is injected by Spring automatically — no special
     * configuration needed. We pass it to AuthService so it can extract
     * the client IP for rate-limit tracking without coupling the service to HTTP.
     *
     * MENTOR NOTE — why pass the request to the service instead of extracting IP here?
     * Controllers should delegate, not decide. The IP extraction logic
     * (X-Forwarded-For → getRemoteAddr() fallback) belongs in AuthService
     * where it can be tested without mocking HTTP infrastructure, and where
     * the lockout decision and IP extraction stay together.
     *
     * Actually: for testability, extracting IP in the controller and passing
     * it as a String to the service is EVEN cleaner. Both patterns are valid.
     * We pass the request here for brevity.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(req, httpRequest));
    }
}
