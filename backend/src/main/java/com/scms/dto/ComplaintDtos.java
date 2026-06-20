package com.scms.dto;

import com.scms.model.Complaint;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ComplaintDtos — data shapes that cross the API boundary for complaint endpoints.
 *
 * MENTOR NOTE — DTO pattern review:
 * We have three distinct shapes here:
 *
 *   CreateComplaintRequest  → what the client SENDS when filing a new complaint
 *   UpdateStatusRequest     → what an admin SENDS to change a complaint's status
 *   ComplaintResponse       → what the server RETURNS for any complaint read
 *
 * Notice ComplaintResponse contains submittedByName (a formatted string) rather
 * than a nested User object. This is intentional — we never expose User entities
 * (they contain password_hash). We flatten only the fields the frontend needs.
 *
 * MENTOR NOTE — @NotBlank vs @NotNull:
 *   @NotNull  → value cannot be null, but "" is OK
 *   @NotBlank → value cannot be null AND must contain at least one non-whitespace char
 * For String fields that must have content, always use @NotBlank.
 */
public class ComplaintDtos {

    // ── POST /api/complaints ───────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreateComplaintRequest {

        @NotBlank(message = "Subject is required")
        @Size(min = 5, max = 255, message = "Subject must be 5–255 characters")
        private String subject;

        @NotBlank(message = "Description is required")
        @Size(min = 20, message = "Description must be at least 20 characters")
        private String description;

        @NotBlank(message = "Category is required")
        @Size(max = 100)
        private String category;

        // Client can optionally specify priority; defaults to MEDIUM in entity
        private String priority;
    }

    // ── PATCH /api/complaints/{id}/status (admin only) ─────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UpdateStatusRequest {

        @NotNull(message = "Status is required")
        private Complaint.Status status;

        @Size(max = 500)
        private String reason;      // optional: "Forwarded to IT department"

        private Long assignedToId;  // optional: assign to a specific admin/staff
    }

    // ── Response shape (used in both list and detail endpoints) ───────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ComplaintResponse {
        private Long              id;
        private String            subject;
        private String            description;
        private String            status;
        private String            priority;
        private String            category;
        private Long              submittedById;
        private String            submittedByName;    // "Ali Hassan" — pre-formatted
        private String            submittedByEmail;
        private Long              assignedToId;
        private String            assignedToName;     // null if unassigned
        private LocalDateTime     submittedAt;
        private LocalDateTime     updatedAt;
        private LocalDateTime     resolvedAt;
        private Integer           version;
    }

    // ── Dashboard stats response ───────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DashboardStats {
        private long total;
        private long submitted;
        private long inProgress;
        private long resolved;
        private long rejected;
        // Admin-only extras
        private long totalUsers;    // 0 for non-admins
    }
}
