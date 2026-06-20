package com.scms.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * AuditLog — system-wide, append-only audit trail for every significant action.
 *
 * MENTOR NOTE — Difference from ComplaintVersion:
 *   ComplaintVersion  → structured, per-complaint history (typed columns)
 *   AuditLog          → raw JSON blobs, covers ALL entity types (User, Complaint, Category)
 *
 * AuditLog is your forensic record. If a complaint disappears, you can
 * query audit_logs for entity_type='COMPLAINT', action='SOFT_DELETE'
 * to find who deleted it and when — even if the ComplaintVersion is gone.
 *
 * MENTOR NOTE — old_values / new_values as TEXT (JSON):
 * We store JSON snapshots rather than column-by-column diffs because:
 *   1. Schema changes don't require AuditLog migration
 *   2. You can store heterogeneous entities (User, Complaint) in one table
 *   3. Debugging is easy — just read the JSON directly in MySQL Workbench
 *
 * For querying, MySQL 8+ supports JSON_EXTRACT() on TEXT columns, so you
 * can still filter: WHERE JSON_EXTRACT(new_values, '$.status') = 'RESOLVED'
 */
@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;          // "COMPLAINT", "USER", "CATEGORY"

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 50)
    private String action;              // "CREATE", "UPDATE", "DELETE", "LOGIN"

    @Column(name = "performed_by")
    private Long performedBy;           // nullable: system actions have no user

    @Column(name = "performed_at", updatable = false)
    private LocalDateTime performedAt;

    @Column(name = "old_values", columnDefinition = "TEXT")
    private String oldValues;           // JSON snapshot of before-state

    @Column(name = "new_values", columnDefinition = "TEXT")
    private String newValues;           // JSON snapshot of after-state

    @Column(name = "ip_address", length = 45)   // IPv6 can be up to 39 chars
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        performedAt = LocalDateTime.now();
    }
}
