package com.scms.repository;

import com.scms.model.Complaint;
import com.scms.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ComplaintRepository — data access layer for Complaint entities.
 *
 * CHANGE in v2.0 (production hardening) — the N+1 query fix:
 *
 * v1.3's AdminService.listAllUsers() and getTopComplainants() called
 * complaintRepository.countBySubmittedBy(u) and countBySubmittedByAndStatus()
 * inside a per-user loop — 3N+1 SQL queries for N users. getReportSummary()
 * issued 7 separate countByStatus() calls where one GROUP BY query suffices.
 *
 * countPerUserGroupedByStatus() and countGroupedByStatus() below replace all
 * of that with ONE query each, executed once regardless of how many users or
 * statuses exist. AdminReportService aggregates the flat result list into
 * the same Map<userId, Map<status,count>> shape the old code built with 3N
 * queries — same output, one round-trip to the database.
 */
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    // ── Projection interfaces for aggregate queries ────────────────────────

    interface StatusCount {
        Complaint.Status getStatus();
        Long getTotal();
    }

    interface UserStatusCount {
        Long getUserId();
        Complaint.Status getStatus();
        Long getTotal();
    }

    interface CategoryCount {
        String getCategoryName();
        Long getTotal();
    }

    // ── User-scoped queries (students see only their own) ─────────────────

    Page<Complaint> findBySubmittedBy(User submittedBy, Pageable pageable);

    Page<Complaint> findBySubmittedByAndStatus(User submittedBy, Complaint.Status status, Pageable pageable);

    long countBySubmittedBy(User submittedBy);

    long countBySubmittedByAndStatus(User submittedBy, Complaint.Status status);

    // ── Staff/admin assignment queries ──────────────────────────────────────

    Page<Complaint> findByAssignedTo(User assignedTo, Pageable pageable);

    Page<Complaint> findByAssignedToAndStatus(User assignedTo, Complaint.Status status, Pageable pageable);

    long countByAssignedTo(User assignedTo);

    long countByAssignedToAndStatus(User assignedTo, Complaint.Status status);

    /**
     * The staff "pick-up queue" — unassigned, still-open complaints, ordered
     * by priority (CRITICAL first) then by age (oldest first — FIFO within
     * the same priority). This single indexed/ordered query replaces the
     * v1.3 pattern of loading every complaint and sorting in JavaScript on
     * the frontend (StaffQueue.jsx used to call complaintsApi.list() and
     * filter+sort the entire dataset client-side).
     */
    @Query("SELECT c FROM Complaint c WHERE c.assignedTo IS NULL " +
           "AND c.status NOT IN ('RESOLVED','CLOSED','REJECTED') " +
           "ORDER BY CASE c.priority " +
           "  WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, " +
           "c.submittedAt ASC")
    Page<Complaint> findUnassignedQueue(Pageable pageable);

    // ── Status-scoped queries ───────────────────────────────────────────────

    long countByStatus(Complaint.Status status);

    List<Complaint> findByStatusOrderBySubmittedAtDesc(Complaint.Status status);

    // ── Admin — all complaints, paginated ───────────────────────────────────

    Page<Complaint> findAll(Pageable pageable);

    // ── Aggregate / reporting queries (replace the v1.3 N+1 loops) ─────────

    @Query("SELECT c.status as status, COUNT(c) as total FROM Complaint c GROUP BY c.status")
    List<StatusCount> countGroupedByStatus();

    @Query("SELECT c.submittedBy.id as userId, c.status as status, COUNT(c) as total " +
           "FROM Complaint c GROUP BY c.submittedBy.id, c.status")
    List<UserStatusCount> countPerUserGroupedByStatus();

    @Query("SELECT c.category.name as categoryName, COUNT(c) as total " +
           "FROM Complaint c GROUP BY c.category.name")
    List<CategoryCount> countGroupedByCategory();

    long countBySubmittedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Bounded by definition to a 30-day window for the activity timeline
     * chart — unlike the v1.3 findAllByOrderBySubmittedAtDesc() (which the
     * report correctly flagged for loading the ENTIRE table), this can never
     * return more rows than the complaint volume of a single month.
     */
    List<Complaint> findBySubmittedAtBetween(LocalDateTime start, LocalDateTime end);

    // ── Search (admin) ────────────────────────────────────────────────────

    @Query("SELECT c FROM Complaint c WHERE LOWER(c.subject) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(c.category.name) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Complaint> search(@Param("q") String query, Pageable pageable);

    @Query("SELECT c FROM Complaint c WHERE c.status = :status AND " +
           "(LOWER(c.subject) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(c.category.name) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Complaint> searchByStatus(@Param("status") Complaint.Status status,
                                    @Param("q") String query, Pageable pageable);

    Page<Complaint> findByStatus(Complaint.Status status, Pageable pageable);
}
