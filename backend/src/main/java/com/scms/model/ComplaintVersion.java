package com.scms.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ComplaintVersion — immutable snapshot of a complaint at each change point.
 *
 * MENTOR NOTE — Event Sourcing lite:
 * We never overwrite history. Every time a complaint's status, priority, or
 * assignment changes, we INSERT a new ComplaintVersion row. This gives us:
 *   1. Full audit trail ("when did this become IN_PROGRESS? Who did it?")
 *   2. Rollback potential (read version N-1 to undo)
 *   3. Regulatory compliance (complaint grievance systems often require audit logs)
 *
 * Notice there are NO @PreUpdate or @Version here — ComplaintVersion rows
 * are written once and never updated. They are append-only.
 *
 * MENTOR NOTE — Why not just use AuditLog?
 * AuditLog (system-wide) records raw JSON blobs of before/after state.
 * ComplaintVersion records structured, queryable columns — you can do:
 *   SELECT * FROM complaint_versions WHERE complaint_id = 42 ORDER BY version_number
 * without parsing JSON. Both serve different use-cases.
 */
@Entity
@Table(name = "complaint_versions")
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

    // ── Reference to the parent complaint ────────────────────────────────
    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

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

    @Column(name = "assigned_to")
    private Long assignedToId;

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

    // ── Status transition record ──────────────────────────────────────────
    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    @Column(name = "new_status", length = 30)
    private String newStatus;

    @PrePersist
    protected void onCreate() {
        changedAt = LocalDateTime.now();
    }
}
