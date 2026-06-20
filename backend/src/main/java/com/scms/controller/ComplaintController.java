package com.scms.controller;

import com.scms.dto.ComplaintDtos.*;
import com.scms.model.User;
import com.scms.repository.UserRepository;
import com.scms.service.ComplaintService;
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
 * ComplaintController — HTTP layer for all complaint operations.
 *
 * MENTOR NOTE — @AuthenticationPrincipal:
 * Spring Security stores the authenticated User in the SecurityContext.
 * @AuthenticationPrincipal injects it directly into the method parameter.
 * This works because JwtAuthFilter calls:
 *   SecurityContextHolder.getContext().setAuthentication(
 *       new UsernamePasswordAuthenticationToken(userDetails, null, authorities)
 *   )
 * And UserDetailsServiceImpl returns our User entity (which implements UserDetails).
 * So @AuthenticationPrincipal gives us the full User object — no DB lookup needed.
 *
 * MENTOR NOTE — Role checking pattern:
 * actor.getAuthorities() returns ["ROLE_ADMIN", "ROLE_USER"].
 * We check isAdmin by looking for "ROLE_ADMIN" in that collection.
 * This is safe because roles come from the JWT → DB → SecurityContext chain,
 * not from anything the client sends.
 *
 * MENTOR NOTE — URL design:
 *   GET  /api/complaints           → list (student: own; admin: all)
 *   POST /api/complaints           → create new complaint
 *   GET  /api/complaints/{id}      → get single complaint
 *   PATCH /api/complaints/{id}/status → update status (admin only)
 *   DELETE /api/complaints/{id}    → soft-delete
 *   GET  /api/complaints/stats     → dashboard statistics
 *
 * We use PATCH (not PUT) for status update because we're changing part of
 * the resource, not replacing it entirely. This follows REST semantics.
 */
@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;
    private final UserRepository   userRepository;

    // ── POST /api/complaints ───────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ComplaintResponse> create(
            @Valid @RequestBody CreateComplaintRequest req,
            @AuthenticationPrincipal User actor) {

        ComplaintResponse res = complaintService.createComplaint(req, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    // ── GET /api/complaints ────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<ComplaintResponse>> list(
            @AuthenticationPrincipal User actor) {

        boolean admin = isAdmin(actor);
        List<ComplaintResponse> list = admin
                ? complaintService.getAllComplaints()
                : complaintService.getMyComplaints(actor);

        return ResponseEntity.ok(list);
    }

    // ── GET /api/complaints/{id} ───────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<ComplaintResponse> get(
            @PathVariable Long id,
            @AuthenticationPrincipal User actor) {

        ComplaintResponse res = complaintService.getComplaint(id, actor, isAdmin(actor));
        return ResponseEntity.ok(res);
    }

    // ── PATCH /api/complaints/{id}/status (admin only) ────────────────────

    @PatchMapping("/{id}/status")
    public ResponseEntity<ComplaintResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest req,
            @AuthenticationPrincipal User actor) {

        if (!isAdmin(actor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .build();
        }
        ComplaintResponse res = complaintService.updateStatus(id, req, actor);
        return ResponseEntity.ok(res);
    }

    // ── DELETE /api/complaints/{id} ───────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User actor) {

        complaintService.deleteComplaint(id, actor, isAdmin(actor));
        return ResponseEntity.noContent().build();
    }

    // ── GET /api/complaints/stats ──────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> stats(
            @AuthenticationPrincipal User actor) {

        boolean admin = isAdmin(actor);
        long totalUsers = admin ? userRepository.count() : 0;
        DashboardStats stats = complaintService.getDashboardStats(actor, admin, totalUsers);
        return ResponseEntity.ok(stats);
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

    // ── Helper ────────────────────────────────────────────────────────────

    private boolean isAdmin(User user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
