package com.scms.dto;

import com.scms.model.Complaint;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ComplaintDtos — data shapes that cross the API boundary for complaint endpoints.
 *
 * CHANGE in v2.0:
 *   • CreateComplaintRequest.category (free-text String) → categoryId (Long),
 *     matching the new Category reference table. ComplaintResponse.category
 *     is STILL a String (the category's name) — the API's external shape for
 *     reads is unchanged, so existing report-display code is unaffected.
 *   • UpdateStatusRequest no longer carries assignedToId — assignment is now
 *     its own first-class action (AssignComplaintRequest, via dedicated
 *     /assign endpoints) rather than a side effect bundled into a status
 *     update. This was a real ambiguity in v1.3: "does updating status with
 *     an assignedToId assign-and-transition, or just assign?" is exactly the
 *     kind of implicit multi-purpose endpoint Clean Code principles warn
 *     against.
 *   • ComplaintHistoryResponse / ComplaintVersionEntry expose the
 *     ComplaintVersion audit trail that v1.3 built but never put behind any
 *     API — the v1.3 report explicitly flagged this ("the audit log is never
 *     queried through any API").
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

        @NotNull(message = "Category is required")
        private Long categoryId;

        // Client can optionally specify priority; defaults to MEDIUM in entity
        private String priority;
    }

    // ── PATCH /api/complaints/{id}/status ──────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UpdateStatusRequest {

        @NotNull(message = "Status is required")
        private Complaint.Status status;

        @Size(max = 500)
        private String reason;      // optional: "Forwarded to IT department"
    }

    // ── POST /api/complaints/{id}/assign  (admin) ──────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class AssignComplaintRequest {
        @NotNull(message = "assigneeId is required")
        private Long assigneeId;
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
        private Long               categoryId;
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
        // Staff-only extras
        private long myQueueCount;  // 0 for non-staff
        private long unassignedCount;
    }

    // ── GET /api/complaints/{id}/history ───────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ComplaintVersionEntry {
        private Integer          versionNumber;
        private String           changeType;
        private String           previousStatus;
        private String           newStatus;
        private String           changedByName;
        private String           changeReason;
        private LocalDateTime    changedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ComplaintHistoryResponse {
        private Long                          complaintId;
        private List<ComplaintVersionEntry>   versions;
    }
}
