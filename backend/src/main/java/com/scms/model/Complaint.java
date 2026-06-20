package com.scms.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * Complaint — the central entity of the SCMS system.
 *
 * MENTOR NOTE — @SQLRestriction:
 * We use soft-delete (is_deleted = false). Rather than filtering manually
 * on every query, @SQLRestriction appends a WHERE clause automatically to
 * every JPA query on this entity. It is a Hibernate 6 replacement for the
 * deprecated @Where annotation. This means:
 *   complaintRepository.findAll() → SELECT * FROM complaints WHERE is_deleted = false
 * Deleted complaints are invisible to JPA by default — you'd need a native
 * query to ever see them, which is exactly what you want.
 *
 * MENTOR NOTE — @Enumerated(EnumType.STRING):
 * Hibernate stores the enum as a VARCHAR ("SUBMITTED", "RESOLVED", …) not
 * an integer. STRING is always preferred — if you reorder the enum later,
 * ORDINAL would corrupt your data.
 *
 * MENTOR NOTE — version field (optimistic locking):
 * @Version tells Hibernate to check this column on every UPDATE. If two
 * users fetch version=1 and both try to save, the second one gets an
 * OptimisticLockException instead of silently overwriting the first user's
 * change. Essential for concurrent complaint status updates.
 */
@Entity
@Table(name = "complaints")
@SQLRestriction("is_deleted = false")   // soft-delete filter applied globally
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Complaint {

    // ── Status lifecycle ──────────────────────────────────────────────────
    public enum Status {
        DRAFT, SUBMITTED, IN_REVIEW, IN_PROGRESS, RESOLVED, CLOSED, REJECTED
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    // ── Primary key ───────────────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Core fields ───────────────────────────────────────────────────────
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

    @Column(length = 100)
    private String category;    // free-text category for now; replace with FK later

    // ── Relationships ─────────────────────────────────────────────────────
    /**
     * The user who filed this complaint.
     * MENTOR NOTE — FetchType.LAZY: only load the User when .getSubmittedBy()
     * is actually called. This prevents N+1 queries when listing complaints.
     * We use EAGER for roles (small, always needed) and LAZY here (large objects
     * only needed in detail views).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    /**
     * The admin/staff assigned to handle this complaint. Nullable — complaints
     * start unassigned and an admin picks them up later.
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
