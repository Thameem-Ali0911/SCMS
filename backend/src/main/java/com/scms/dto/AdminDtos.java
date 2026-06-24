package com.scms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * AdminDtos — data transfer shapes for the admin module.
 *
 * MENTOR NOTE — DTOs stay in the API contract, not the entity layer.
 * UserResponse deliberately omits password_hash. Always think:
 * "what does the frontend actually need?" and expose only that.
 *
 * CHANGE in v2.0: ChangeRoleRequest.role now accepts "USER", "STAFF", or
 * "ADMIN" (validated against com.scms.common.Roles.isValid() in
 * AdminUserService) — v1.3 only ever supported a USER/ADMIN toggle.
 * StaffWorkload is new — powers the admin "Assignments" page so an admin can
 * see each staff member's current load before assigning a new complaint to
 * them.
 */
public class AdminDtos {

    // ── User management responses ──────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserResponse {
        private Long            id;
        private String          firstName;
        private String          lastName;
        private String          email;
        private String          phone;
        private boolean         active;
        private Set<String>     roles;
        private LocalDateTime   createdAt;
        private long            totalComplaints;
        private long            openComplaints;
        private long            resolvedComplaints;
    }

    // ── User mutation request bodies ────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ToggleUserStatusRequest {
        @NotNull(message = "active flag is required")
        private Boolean active;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ChangeRoleRequest {
        @NotBlank(message = "role is required")
        private String role;     // "USER", "STAFF", or "ADMIN"
    }

    // ── Staff workload (for the assignment workflow) ────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StaffWorkload {
        private Long   staffId;
        private String staffName;
        private String email;
        private long   assignedOpen;       // currently assigned, not yet resolved/closed/rejected
        private long   resolvedTotal;      // lifetime resolved count — a rough "experience" signal
    }

    // ── Report response shapes ───────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReportSummary {
        private long   totalComplaints;
        private long   openComplaints;
        private long   resolvedComplaints;
        private long   rejectedComplaints;
        private long   totalUsers;
        private long   activeUsers;
        private double avgResolutionHours;
        private long   complaintsThisMonth;
        private long   complaintsLastMonth;
        private double monthOverMonthChange;
        private long   unassignedComplaints;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatusBreakdown {
        private String status;
        private long   count;
        private double percentage;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CategoryBreakdown {
        private String category;
        private long   count;
        private double percentage;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserComplaintCount {
        private Long   userId;
        private String userName;
        private String email;
        private long   total;
        private long   open;
        private long   resolved;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyCount {
        private LocalDate date;
        private long      count;
    }
}
