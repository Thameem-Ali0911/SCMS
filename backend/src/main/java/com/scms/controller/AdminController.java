package com.scms.controller;

import com.scms.dto.AdminDtos.*;
import com.scms.model.User;
import com.scms.service.AdminService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AdminController — HTTP layer for all admin-only operations.
 *
 * MENTOR NOTE — Security layers:
 * 1. SecurityConfig permits ALL authenticated requests to /api/admin/**
 * 2. Each method here calls isAdmin(actor) and returns 403 if not.
 * 3. AdminService also enforces the rules — two-layer defence-in-depth.
 *
 * All endpoints under /api/admin/** are semantically "admin-only",
 * but we enforce it programmatically via role-check rather than
 * Spring Security's .hasRole("ADMIN") DSL so the error messages
 * are consistent and JSON-formatted like the rest of our API.
 *
 * Endpoints:
 *   GET    /api/admin/users              → list all users (pageable)
 *   GET    /api/admin/users/{id}         → get user detail
 *   PATCH  /api/admin/users/{id}/status  → activate / deactivate user
 *   PATCH  /api/admin/users/{id}/role    → change user role
 *   DELETE /api/admin/users/{id}         → soft-deactivate user
 *   GET    /api/admin/reports/summary    → aggregate complaint stats
 *   GET    /api/admin/reports/by-status  → breakdown by status
 *   GET    /api/admin/reports/by-category→ breakdown by category
 *   GET    /api/admin/reports/by-user    → top complainants
 *   GET    /api/admin/reports/timeline   → daily complaint count (last 30d)
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ═══════════════════════════════════════════════════════════════════════
    //  USER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /** GET /api/admin/users — list every registered user */
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> listUsers(
            @AuthenticationPrincipal User actor) {

        requireAdmin(actor);
        return ResponseEntity.ok(adminService.listAllUsers());
    }

    /** GET /api/admin/users/{id} — get a single user's profile */
    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUser(
            @PathVariable Long id,
            @AuthenticationPrincipal User actor) {

        requireAdmin(actor);
        return ResponseEntity.ok(adminService.getUserById(id));
    }

    /**
     * PATCH /api/admin/users/{id}/status — toggle active/inactive.
     * An inactive user's JWT is rejected at login time (isEnabled() → false).
     * They cannot log in again until re-activated.
     */
    @PatchMapping("/users/{id}/status")
    public ResponseEntity<UserResponse> toggleUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody ToggleUserStatusRequest req,
            @AuthenticationPrincipal User actor) {

        requireAdmin(actor);
        // Prevent admin from deactivating themselves
        if (id.equals(actor.getId())) {
            return ResponseEntity.badRequest()
                    .build();
        }
        return ResponseEntity.ok(adminService.toggleUserStatus(id, req.isActive(), actor));
    }

    /**
     * PATCH /api/admin/users/{id}/role — promote USER → ADMIN or demote ADMIN → USER.
     * Prevents self-demotion so there is always at least one admin.
     */
    @PatchMapping("/users/{id}/role")
    public ResponseEntity<UserResponse> changeUserRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest req,
            @AuthenticationPrincipal User actor) {

        requireAdmin(actor);
        if (id.equals(actor.getId())) {
            return ResponseEntity.badRequest()
                    .build();
        }
        return ResponseEntity.ok(adminService.changeUserRole(id, req.getRole(), actor));
    }

    /**
     * DELETE /api/admin/users/{id} — deactivate (soft-disable) a user account.
     * We never hard-delete users — their complaints must remain traceable.
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal User actor) {

        requireAdmin(actor);
        if (id.equals(actor.getId())) {
            return ResponseEntity.badRequest().build();
        }
        adminService.deactivateUser(id, actor);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  REPORTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * GET /api/admin/reports/summary — top-level KPIs for the report dashboard.
     * Returns total users, total complaints, avg resolution time, etc.
     */
    @GetMapping("/reports/summary")
    public ResponseEntity<ReportSummary> reportSummary(
            @AuthenticationPrincipal User actor) {

        requireAdmin(actor);
        return ResponseEntity.ok(adminService.getReportSummary());
    }

    /**
     * GET /api/admin/reports/by-status — complaint count per status.
     * Used to render the pie / donut chart on the Reports page.
     */
    @GetMapping("/reports/by-status")
    public ResponseEntity<List<StatusBreakdown>> reportByStatus(
            @AuthenticationPrincipal User actor) {

        requireAdmin(actor);
        return ResponseEntity.ok(adminService.getStatusBreakdown());
    }

    /**
     * GET /api/admin/reports/by-category — complaint count per category.
     * Used to render the bar chart.
     */
    @GetMapping("/reports/by-category")
    public ResponseEntity<List<CategoryBreakdown>> reportByCategory(
            @AuthenticationPrincipal User actor) {

        requireAdmin(actor);
        return ResponseEntity.ok(adminService.getCategoryBreakdown());
    }

    /**
     * GET /api/admin/reports/by-user — top 10 users by complaint volume.
     * Useful for spotting serial complainants or high-issue departments.
     */
    @GetMapping("/reports/by-user")
    public ResponseEntity<List<UserComplaintCount>> reportByUser(
            @AuthenticationPrincipal User actor) {

        requireAdmin(actor);
        return ResponseEntity.ok(adminService.getTopComplainants());
    }

    /**
     * GET /api/admin/reports/timeline — daily complaint count for the last 30 days.
     * Used to render the line / area chart showing activity trends.
     */
    @GetMapping("/reports/timeline")
    public ResponseEntity<List<DailyCount>> reportTimeline(
            @AuthenticationPrincipal User actor) {

        requireAdmin(actor);
        return ResponseEntity.ok(adminService.getDailyTimeline());
    }

    // ── Exception handlers ────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", ex.getMessage()));
    }

    // ── Guard helper ──────────────────────────────────────────────────────

    private void requireAdmin(User user) {
        boolean admin = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!admin) {
            throw new AccessDeniedException("This endpoint is restricted to administrators.");
        }
    }
}
