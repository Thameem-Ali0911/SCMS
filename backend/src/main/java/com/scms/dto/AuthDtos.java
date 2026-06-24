package com.scms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.Set;

/**
 * AuthDtos — data shapes that cross the API boundary for auth endpoints.
 *
 * CHANGE in v2.0: AuthResponse now contains only the short-lived access
 * token. The refresh token is never placed in a JSON body or localStorage —
 * it is set directly as an HttpOnly cookie by AuthController, invisible to
 * JavaScript (and therefore to an XSS payload). See JwtUtil for the full
 * rationale.
 */
public class AuthDtos {

    // ── POST /api/auth/register ──────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RegisterRequest {

        @NotBlank(message = "First name is required")
        @Size(min = 2, max = 50, message = "First name must be 2–50 characters")
        private String firstName;

        @NotBlank(message = "Last name is required")
        @Size(min = 2, max = 50, message = "Last name must be 2–50 characters")
        private String lastName;

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "Password must contain at least one letter and one number"
        )
        private String password;

        @Size(max = 20)
        private String phone;
    }

    // ── POST /api/auth/login ─────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank @Email
        private String email;

        @NotBlank
        private String password;
    }

    // ── Response from login, register, AND /api/auth/refresh ──────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AuthResponse {
        private String      accessToken;
        private long         expiresInSeconds;
        private Long         userId;
        private String       firstName;
        private String       lastName;
        private String       email;
        private Set<String>  roles;       // e.g. ["USER"], ["STAFF"], ["ADMIN"]
    }
}
