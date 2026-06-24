package com.scms.controller;

import com.scms.dto.AdminDtos.*;
import com.scms.dto.CategoryDtos.CategoryRequest;
import com.scms.dto.CategoryDtos.CategoryResponse;
import com.scms.dto.PageResponse;
import com.scms.model.User;
import com.scms.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AdminController — HTTP layer for all admin-only operations.
 *
 * CHANGE in v2.0 (production hardening):
 *
 *   • @PreAuthorize("hasRole('ADMIN')") at the CLASS level replaces v1.3's
 *     manual requireAdmin(actor) call at the top of every single method.
 *     The report's exact words: "AdminController ignores Spring Security's
 *     .hasRole('ADMIN') DSL and implements role checking manually in every
 *     method — more verbose and error-prone than declarative security."
 *     This is also enforced again at the URL level in SecurityConfig
 *     (/api/admin/** → hasRole(ADMIN)) — defense in depth, but now BOTH
 *     layers are declarative, not hand-written.
 *
 *   • Delegates to four focused services (AdminUserService,
 *     AdminReportService, AdminAssignmentService, CategoryService) instead
 *     of one 16KB god-class — see each service's javadoc for what moved
 *     where and why.
 *
 *   • New: GET /api/admin/staff (workload), POST /api/admin/categories
 *     (category CRUD) — the assignment workflow and category-reference-table
 *     fixes this version adds.
 *
 *   • Local @ExceptionHandler methods removed (duplicated GlobalExceptionHandler).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminUserService       adminUserService;
    private final AdminReportService     adminReportService;
    private final AdminAssignmentService adminAssignmentService;
    private final CategoryService        categoryService;

    // ── User management ──────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<PageResponse<UserResponse>> listUsers(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminUserService.listAllUsers(pageable, q));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.getUserById(id));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<UserResponse> toggleUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody ToggleUserStatusRequest req,
            @AuthenticationPrincipal User actor) {
        if (id.equals(actor.getId())) {
            throw new IllegalArgumentException("You cannot deactivate your own account.");
        }
        return ResponseEntity.ok(adminUserService.toggleUserStatus(id, req.getActive(), actor));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<UserResponse> changeUserRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest req,
            @AuthenticationPrincipal User actor) {
        if (id.equals(actor.getId())) {
            throw new IllegalArgumentException("You cannot change your own role.");
        }
        return ResponseEntity.ok(adminUserService.changeUserRole(id, req.getRole(), actor));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        if (id.equals(actor.getId())) {
            throw new IllegalArgumentException("You cannot deactivate your own account.");
        }
        adminUserService.deactivateUser(id, actor);
        return ResponseEntity.noContent().build();
    }

    // ── Reports ──────────────────────────────────────────────────────────

    @GetMapping("/reports/summary")
    public ResponseEntity<ReportSummary> reportSummary() {
        return ResponseEntity.ok(adminReportService.getReportSummary());
    }

    @GetMapping("/reports/by-status")
    public ResponseEntity<List<StatusBreakdown>> reportByStatus() {
        return ResponseEntity.ok(adminReportService.getStatusBreakdown());
    }

    @GetMapping("/reports/by-category")
    public ResponseEntity<List<CategoryBreakdown>> reportByCategory() {
        return ResponseEntity.ok(adminReportService.getCategoryBreakdown());
    }

    @GetMapping("/reports/by-user")
    public ResponseEntity<List<UserComplaintCount>> reportByUser() {
        return ResponseEntity.ok(adminReportService.getTopComplainants());
    }

    @GetMapping("/reports/timeline")
    public ResponseEntity<List<DailyCount>> reportTimeline() {
        return ResponseEntity.ok(adminReportService.getDailyTimeline());
    }

    // ── Staff assignment workflow ───────────────────────────────────────
    // NOTE: assigning a complaint to a staff member is POST
    // /api/complaints/{id}/assign (in ComplaintController, @PreAuthorize
    // "hasRole('ADMIN')") rather than duplicated here — one endpoint, one
    // owner, consistent with the rest of the v2.0 cleanup that removed
    // duplicated logic between controllers.

    @GetMapping("/staff")
    public ResponseEntity<List<StaffWorkload>> staffWorkload() {
        return ResponseEntity.ok(adminAssignmentService.getStaffWorkload());
    }


    // ── Category management ─────────────────────────────────────────────

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> listAllCategories() {
        return ResponseEntity.ok(categoryService.listAll());
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest req) {
        return ResponseEntity.ok(categoryService.create(req));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        return ResponseEntity.ok(categoryService.update(id, req));
    }

    @PatchMapping("/categories/{id}/active")
    public ResponseEntity<CategoryResponse> setCategoryActive(
            @PathVariable Long id, @RequestParam boolean active) {
        return ResponseEntity.ok(categoryService.setActive(id, active));
    }
}
