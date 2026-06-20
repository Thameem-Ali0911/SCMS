package com.scms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.Set;

/**
 * AuthDtos — data shapes that cross the API boundary for auth endpoints.
 *
 * MENTOR NOTE — Why DTOs, not entities?
 * 1. Security  → User entity has password_hash; we never want that in a JSON response.
 * 2. Decoupling → API contract stays stable even if the DB schema changes.
 * 3. Validation → @NotBlank, @Email etc. live on the DTO, not the entity.
 *                 The entity is the DB truth; the DTO is the API contract.
 *
 * All classes are static nested classes inside AuthDtos so they're
 * imported together: import com.scms.dto.AuthDtos.*
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

    // ── Response from both login and register ────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AuthResponse {
        private String      token;       // JWT — store in localStorage on the client
        private Long        userId;
        private String      firstName;
        private String      lastName;
        private String      email;
        private Set<String> roles;       // e.g. ["USER"] or ["ADMIN"]
    }
}
