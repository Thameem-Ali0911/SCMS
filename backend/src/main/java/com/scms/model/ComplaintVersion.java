package com.scms.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ComplaintVersion — immutable snapshot of a complaint at each change point.
 *
 * CHANGE in v2.0 (production hardening):
 *   complaintId (a bare Long) is now a proper @ManyToOne FK to Complaint.
 *   v1.3 finding: "ComplaintVersion uses complaintId as a plain Long column
 *   instead of a @ManyToOne FK with referential integrity — you can insert
 *   an orphan version record for a complaint that doesn't exist." The FK
 *   constraint now makes that insertion impossible at the database level,
 *   not just by convention in application code.
 *
 *   assignedToId is similarly upgraded to a real @ManyToOne FK (nullable —
 *   a version snapshot taken before assignment legitimately has no assignee).
 */
@Entity
@Table(name = "complaint_versions", indexes = {
        @Index(name = "idx_complaint_versions_complaint_id", columnList = "complaint_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintVersion {

    public enum ChangeType {
        CREATE, UPDATE, STATUS_CHANGE, ASSIGN, RESOLVE, CLOSE, SOFT_DELETE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    // ── Snapshot of complaint state at this version ───────────────────────
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 30)
    private String status;

    @Column(length = 15)
    private String priority;

    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    // ── Who changed it and when ───────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @Column(name = "changed_at", updatable = false)
    private LocalDateTime changedAt;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 20)
    private ChangeType changeType;

    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    @Column(name = "new_status", length = 30)
    private String newStatus;

    @PrePersist
    protected void onCreate() {
        changedAt = LocalDateTime.now();
    }
}
