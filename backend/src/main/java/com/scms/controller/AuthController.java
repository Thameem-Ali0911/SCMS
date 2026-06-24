package com.scms.controller;

import com.scms.dto.AuthDtos.*;
import com.scms.model.User;
import com.scms.service.AuthService;
import com.scms.service.AuthService.AuthResult;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — REST endpoints for register, login, refresh, and logout.
 *
 * CHANGE in v2.0 (production hardening):
 *
 *   • The refresh token is set as an HttpOnly cookie here — and ONLY here.
 *     It never appears in a JSON response body. Scoped to Path=/api/auth so
 *     the browser doesn't attach it to every single API call, only to the
 *     auth endpoints that need it (refresh, logout).
 *
 *   • POST /api/auth/refresh — silently exchanges a valid refresh cookie for
 *     a new access token (and rotates the refresh cookie). The frontend
 *     calls this transparently when an access token expires — see
 *     frontend/src/api/axios.js's response interceptor.
 *
 *   • POST /api/auth/logout — revokes all of the user's tokens (via
 *     AuthService.logout → User.tokenVersion bump) and clears the cookie.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "scms_refresh";
    private static final String REFRESH_COOKIE_PATH  = "/api/auth";

    private final AuthService authService;

    @Value("${cookie.secure:true}")
    private boolean cookieSecure;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        AuthResult result = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result).toString())
                .body(result.getBody());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        AuthResult result = authService.login(req);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result).toString())
                .body(result.getBody());
    }

    /**
     * Reads the refresh cookie directly (rather than via @RequestBody) — the
     * browser attaches it automatically because of Path=/api/auth, so the
     * frontend's refresh call needs no payload at all, just
     * `axios.post('/auth/refresh', {}, { withCredentials: true })`.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        AuthResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result).toString())
                .body(result.getBody());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal User user) {
        if (user != null) {
            authService.logout(user);
        }
        ResponseCookie cleared = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    private ResponseCookie buildRefreshCookie(AuthResult result) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, result.getRefreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(result.getRefreshExpiresInSeconds())
                .build();
    }
}
