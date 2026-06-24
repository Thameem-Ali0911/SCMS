package com.scms.controller;

import com.scms.dto.ComplaintDtos.*;
import com.scms.dto.PageResponse;
import com.scms.model.Complaint;
import com.scms.model.User;
import com.scms.repository.UserRepository;
import com.scms.service.ComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * ComplaintController — HTTP layer for all complaint operations.
 *
 * CHANGE in v2.0 (production hardening):
 *
 *   • Every list endpoint now accepts page/size/sort and returns a
 *     PageResponse instead of a bare array (see PageResponse for the
 *     v1.3 "unbounded findAll()" finding this fixes).
 *
 *   • New queue + assignment endpoints power the STAFF workflow: a staff
 *     member browses /queue/unassigned, self-assigns via /{id}/assign/me,
 *     works the complaint, and updates its status — all gated so a STAFF
 *     user can only act on complaints actually assigned to them (enforced
 *     in ComplaintService.assertCanManage). /{id}/assign lets an ADMIN
 *     assign a complaint to a specific staff/admin user directly.
 *
 *   • Local @ExceptionHandler methods removed — GlobalExceptionHandler now
 *     handles IllegalStateException (409, illegal status transition) and
 *     every other exception type centrally. v1.3's report explicitly
 *     flagged duplicated exception handling between controllers and
 *     GlobalExceptionHandler as a Maintainability issue.
 */
@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;
    private final UserRepository   userRepository;

    @PostMapping
    public ResponseEntity<ComplaintResponse> create(
            @Valid @RequestBody CreateComplaintRequest req,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complaintService.createComplaint(req, actor));
    }

    /**
     * GET /api/complaints — scoped by role:
     *   USER  → their own complaints only
     *   STAFF → complaints currently assigned to them (use /queue/unassigned for the pick-up pool)
     *   ADMIN → every complaint, optionally filtered by status/search
     */
    @GetMapping
    public ResponseEntity<PageResponse<ComplaintResponse>> list(
            @AuthenticationPrincipal User actor,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {

        Pageable sorted = defaultSort(pageable);
        Complaint.Status statusEnum = parseStatus(status);

        if (isAdmin(actor)) {
            return ResponseEntity.ok(toPageResponse(complaintService.getAllComplaints(sorted, statusEnum, q)));
        }
        if (isStaff(actor)) {
            return ResponseEntity.ok(toPageResponse(complaintService.getMyQueue(actor, sorted, statusEnum)));
        }
        return ResponseEntity.ok(toPageResponse(complaintService.getMyComplaints(actor, sorted, statusEnum)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComplaintResponse> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(complaintService.getComplaint(id, actor));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<ComplaintHistoryResponse> history(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(complaintService.getHistory(id, actor));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ComplaintResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest req,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(complaintService.updateStatus(id, req, actor, isAdmin(actor)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        complaintService.deleteComplaint(id, actor, isAdmin(actor));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> stats(@AuthenticationPrincipal User actor) {
        boolean admin = isAdmin(actor);
        boolean staff = isStaff(actor);
        long totalUsers = admin ? userRepository.count() : 0;
        return ResponseEntity.ok(complaintService.getDashboardStats(actor, admin, staff, totalUsers));
    }

    // ── Queue + assignment (STAFF / ADMIN) ─────────────────────────────────

    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @GetMapping("/queue/unassigned")
    public ResponseEntity<PageResponse<ComplaintResponse>> unassignedQueue(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(toPageResponse(complaintService.getUnassignedQueue(defaultSort(pageable))));
    }

    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @GetMapping("/queue/mine")
    public ResponseEntity<PageResponse<ComplaintResponse>> myQueue(
            @AuthenticationPrincipal User actor,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(toPageResponse(
                complaintService.getMyQueue(actor, defaultSort(pageable), parseStatus(status))));
    }

    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @PostMapping("/{id}/assign/me")
    public ResponseEntity<ComplaintResponse> selfAssign(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(complaintService.selfAssign(id, actor));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/assign")
    public ResponseEntity<ComplaintResponse> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignComplaintRequest req,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(complaintService.assignTo(id, req.getAssigneeId(), actor));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Pageable defaultSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("submittedAt").descending());
        }
        return pageable;
    }

    private Complaint.Status parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Complaint.Status.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private PageResponse<ComplaintResponse> toPageResponse(org.springframework.data.domain.Page<ComplaintResponse> page) {
        return PageResponse.of(page, c -> c);
    }

    private boolean isAdmin(User user) {
        return user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean isStaff(User user) {
        return user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));
    }
}
