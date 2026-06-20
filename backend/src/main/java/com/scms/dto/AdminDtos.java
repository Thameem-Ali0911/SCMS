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
 * Nested static classes grouped under AdminDtos so they're imported
 * together:  import com.scms.dto.AdminDtos.*;
 */
public class AdminDtos {

    // ── User management responses ──────────────────────────────────────────

    /**
     * Full user profile sent to the admin Users page.
     * Includes complaint count so we don't need a second API call.
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserResponse {
        private Long            id;
        private String          firstName;
        private String          lastName;
        private String          email;
        private String          phone;
        private boolean         active;
        private Set<String>     roles;          // ["USER"] or ["ADMIN"]
        private LocalDateTime   createdAt;
        private long            totalComplaints;
        private long            openComplaints;  // SUBMITTED + IN_REVIEW + IN_PROGRESS
        private long            resolvedComplaints;
    }

    // ── User mutation request bodies ──────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ToggleUserStatusRequest {
        @NotNull(message = "active flag is required")
        private boolean active;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ChangeRoleRequest {
        @NotBlank(message = "role is required")
        private String role;     // "USER" or "ADMIN"
    }

    // ── Report response shapes ─────────────────────────────────────────────

    /**
     * ReportSummary — top-level KPI snapshot.
     * Drives the summary stat cards at the top of the Reports page.
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReportSummary {
        private long   totalComplaints;
        private long   openComplaints;          // SUBMITTED + IN_REVIEW + IN_PROGRESS
        private long   resolvedComplaints;
        private long   rejectedComplaints;
        private long   totalUsers;
        private long   activeUsers;
        private double avgResolutionHours;      // mean hours from submittedAt → resolvedAt
        private long   complaintsThisMonth;     // filed in the current calendar month
        private long   complaintsLastMonth;
        private double monthOverMonthChange;    // percentage change (can be negative)
    }

    /**
     * StatusBreakdown — one row per complaint status with its count.
     * Used for pie/donut chart on Reports page.
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatusBreakdown {
        private String status;   // "SUBMITTED", "IN_PROGRESS", etc.
        private long   count;
        private double percentage;
    }

    /**
     * CategoryBreakdown — one row per category with its count.
     * Used for horizontal bar chart.
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CategoryBreakdown {
        private String category;
        private long   count;
        private double percentage;
    }

    /**
     * UserComplaintCount — one row per user, ranked by complaint volume.
     * Top-10 list in the Reports page.
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserComplaintCount {
        private Long   userId;
        private String userName;
        private String email;
        private long   total;
        private long   open;
        private long   resolved;
    }

    /**
     * DailyCount — one row per calendar day with complaint count.
     * Last 30 days, used for the area/line chart.
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyCount {
        private LocalDate date;   // "2026-06-01"
        private long      count;
    }
}
