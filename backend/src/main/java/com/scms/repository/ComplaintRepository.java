package com.scms.repository;

import com.scms.model.Complaint;
import com.scms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ComplaintRepository — data access layer for Complaint entities.
 *
 * MENTOR NOTE — Spring Data JPA magic:
 * Spring auto-generates SQL from method names at startup. The rules are:
 *   findBy{Field}          → WHERE field = ?
 *   findBy{Field}OrderBy{OtherField}Desc → WHERE field = ? ORDER BY other_field DESC
 *   countBy{Field}         → SELECT COUNT(*) WHERE field = ?
 *
 * The @SQLRestriction("is_deleted = false") on the Complaint entity means
 * ALL these methods automatically add AND is_deleted = false — zero risk of
 * accidentally returning deleted records.
 *
 * MENTOR NOTE — @Query with JPQL:
 * JPQL (Java Persistence Query Language) looks like SQL but works on entity
 * class names and field names, not table/column names. Hibernate translates
 * it to MySQL. This is preferred over native SQL because it's database-agnostic
 * and benefits from Hibernate's optimisation layer.
 *
 * NEW queries added for AdminService reporting:
 *   countBySubmittedAtBetween   — monthly comparison (MoM change)
 *   findBySubmittedAtBetween    — daily timeline chart (last 30 days)
 */
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    // ── User-scoped queries (students see only their own) ─────────────────

    List<Complaint> findBySubmittedByOrderBySubmittedAtDesc(User submittedBy);

    long countBySubmittedBy(User submittedBy);

    long countBySubmittedByAndStatus(User submittedBy, Complaint.Status status);

    // ── Admin queries (sees all complaints) ──────────────────────────────

    List<Complaint> findAllByOrderBySubmittedAtDesc();

    // ── Dashboard statistics ──────────────────────────────────────────────

    long countByStatus(Complaint.Status status);

    /**
     * Count complaints by status for a specific user.
     * Used on the student dashboard to show "My Open", "My Resolved", etc.
     */
    @Query("SELECT COUNT(c) FROM Complaint c WHERE c.submittedBy = :user AND c.status = :status")
    long countBySubmittedByAndStatusEnum(
            @Param("user")   User user,
            @Param("status") Complaint.Status status);

    // ── Search (admin) ────────────────────────────────────────────────────

    /**
     * Full-text-ish search on subject. LIKE is not ideal for large datasets;
     * the production upgrade path is MySQL FULLTEXT indexes or Elasticsearch.
     * For a college project, LIKE is fine.
     */
    @Query("SELECT c FROM Complaint c WHERE LOWER(c.subject) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY c.submittedAt DESC")
    List<Complaint> searchBySubject(@Param("q") String query);

    /**
     * All complaints filtered by a given status (admin view).
     */
    List<Complaint> findByStatusOrderBySubmittedAtDesc(Complaint.Status status);

    // ── Report / analytics queries (AdminService) ─────────────────────────

    /**
     * Count complaints submitted within a date-time range.
     * Used by AdminService for month-over-month comparison in ReportSummary.
     *
     * MENTOR NOTE — Spring Data derives this from the method name:
     *   findBy + SubmittedAt + Between → WHERE submitted_at BETWEEN :start AND :end
     * The @SQLRestriction is still applied, so deleted complaints are excluded.
     */
    long countBySubmittedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * All complaints submitted within a date-time range.
     * Used by AdminService.getDailyTimeline() to build the 30-day chart series.
     * Returning entities (not projections) is fine here because we only touch
     * the submittedAt field — Hibernate won't lazy-load relations we don't access.
     */
    List<Complaint> findBySubmittedAtBetween(LocalDateTime start, LocalDateTime end);
}
