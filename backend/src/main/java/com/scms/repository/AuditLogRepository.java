package com.scms.repository;

import com.scms.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * AuditLogRepository — system-wide audit trail queries.
 *
 * MENTOR NOTE — append-only pattern:
 * We only ever SAVE to this repository, never UPDATE or DELETE.
 * If you find yourself calling auditLogRepository.delete(...), something
 * is architecturally wrong. Audit logs are sacred — they're the evidence
 * trail for any dispute or investigation.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByPerformedAtDesc(
            String entityType, Long entityId);

    List<AuditLog> findByPerformedByOrderByPerformedAtDesc(Long performedBy);
}
