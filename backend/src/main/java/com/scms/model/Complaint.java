package com.scms.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * Complaint — the central entity of the SCMS system.
 *
 * CHANGE in v2.0 (production hardening):
 *
 *   • category is now a @ManyToOne FK to Category instead of a free-text
 *     VARCHAR. v1.3 finding: "category is free-text with no FK — categories
 *     are inconsistent strings (case sensitivity, typos)". The DTO-level API
 *     contract (ComplaintResponse.category) is still a plain String — we
 *     simply populate it from category.getName() now, so existing frontend
 *     code that reads `complaint.category` keeps working unchanged.
 *
 *   • Status.DRAFT was removed. It was unreachable dead code in v1.3 — no
 *     "save as draft" flow ever set it, and the v1.3 report flagged exactly
 *     this. Complaints are always created directly as SUBMITTED.
 *
 *   • @Table(indexes = …) added on submitted_by, assigned_to, status,
 *     submitted_at, category_id. v1.3 finding: "no database indexes defined
 *     anywhere — every query that filters on these columns does a full
 *     table scan". These are exactly the columns ComplaintRepository filters
 *     and sorts on.
 */
@Entity
@Table(name = "complaints", indexes = {
        @Index(name = "idx_complaints_submitted_by", columnList = "submitted_by"),
        @Index(name = "idx_complaints_assigned_to",  columnList = "assigned_to"),
        @Index(name = "idx_complaints_status",       columnList = "status"),
        @Index(name = "idx_complaints_submitted_at", columnList = "submitted_at"),
        @Index(name = "idx_complaints_category_id",  columnList = "category_id")
})
@SQLRestriction("is_deleted = false")   // soft-delete filter applied globally
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Complaint {

    // ── Status lifecycle — see common.ComplaintStatusPolicy for transitions ─
    public enum Status {
        SUBMITTED, IN_REVIEW, IN_PROGRESS, RESOLVED, CLOSED, REJECTED
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    /** Reference-table FK (v2.0) — replaces the old free-text `category` VARCHAR. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // ── Relationships ─────────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    /**
     * The staff/admin user assigned to handle this complaint. Nullable —
     * complaints start unassigned and a staff member self-assigns, or an
     * admin assigns it, from the queue. See ComplaintService.selfAssign()
     * and AdminAssignmentService.assignToStaff().
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    // ── Timestamps ────────────────────────────────────────────────────────
    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    // ── Soft delete ───────────────────────────────────────────────────────
    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    // ── Optimistic locking ────────────────────────────────────────────────
    @Version
    @Builder.Default
    private Integer version = 1;

    // ── JPA lifecycle hooks ───────────────────────────────────────────────
    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
        updatedAt   = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
