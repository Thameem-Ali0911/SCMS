package com.scms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scms.dto.ComplaintDtos.*;
import com.scms.model.*;
import com.scms.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ComplaintService — core business logic for every complaint operation.
 *
 * MENTOR NOTE — what this service enforces:
 *
 * 1. RBAC at the service layer (not just the controller):
 *    Controllers enforce "is this endpoint reachable by this role?"
 *    Services enforce "can THIS specific user do THIS specific thing to THIS record?"
 *    Example: A USER can only delete their own complaint. Even if somehow a
 *    USER called a controller method, the service would reject it.
 *    Defence-in-depth — two independent checks.
 *
 * 2. Versioning on every state change:
 *    Every mutating method calls recordVersion(). This writes an immutable
 *    snapshot to complaint_versions. It's the audit trail requirement fulfilled.
 *
 * 3. Audit logging on every action:
 *    recordAudit() writes to audit_logs. This is the system-wide forensic record.
 *
 * 4. Soft delete only — we never call repository.delete():
 *    Deleted complaints remain in the DB with is_deleted=true. The @SQLRestriction
 *    on Complaint hides them from all normal queries automatically.
 *
 * MENTOR NOTE — @Transactional:
 *    createComplaint() does 3 writes: complaints, complaint_versions, audit_logs.
 *    @Transactional wraps them atomically. If complaint_versions insert fails,
 *    the complaint insert is rolled back too — no orphaned records.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintService {

    private final ComplaintRepository        complaintRepository;
    private final ComplaintVersionRepository versionRepository;
    private final AuditLogRepository         auditLogRepository;
    private final UserRepository             userRepository;
    private final ObjectMapper               objectMapper;       // Jackson — injected by Spring Boot auto-config

    // ── CREATE ──────────────────────────────────────────────────────────────

    @Transactional
    public ComplaintResponse createComplaint(CreateComplaintRequest req, User actor) {
        // Map priority string → enum (default MEDIUM if null/invalid)
        Complaint.Priority priority;
        try {
            priority = req.getPriority() != null
                    ? Complaint.Priority.valueOf(req.getPriority().toUpperCase())
                    : Complaint.Priority.MEDIUM;
        } catch (IllegalArgumentException e) {
            priority = Complaint.Priority.MEDIUM;
        }

        Complaint complaint = Complaint.builder()
                .subject(req.getSubject().trim())
                .description(req.getDescription().trim())
                .category(req.getCategory().trim())
                .priority(priority)
                .status(Complaint.Status.SUBMITTED)
                .submittedBy(actor)
                .build();

        complaint = complaintRepository.save(complaint);

        // Record initial version (version 1 = creation snapshot)
        recordVersion(complaint, actor, ComplaintVersion.ChangeType.CREATE,
                null, Complaint.Status.SUBMITTED.name(), "Complaint submitted");

        // Audit log
        recordAudit("COMPLAINT", complaint.getId(), "CREATE", actor.getId(),
                null, toJson(complaint));

        log.info("Complaint #{} created by {}", complaint.getId(), actor.getEmail());
        return toResponse(complaint);
    }

    // ── READ — student (own complaints only) ────────────────────────────────

    public List<ComplaintResponse> getMyComplaints(User actor) {
        return complaintRepository
                .findBySubmittedByOrderBySubmittedAtDesc(actor)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── READ — admin (all complaints) ──────────────────────────────────────

    public List<ComplaintResponse> getAllComplaints() {
        return complaintRepository
                .findAllByOrderBySubmittedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── READ — single complaint (access-controlled) ────────────────────────

    public ComplaintResponse getComplaint(Long id, User actor, boolean isAdmin) {
        Complaint complaint = findOrThrow(id);
        // Students can only see their own
        if (!isAdmin && !complaint.getSubmittedBy().getId().equals(actor.getId())) {
            throw new AccessDeniedException("You do not have access to this complaint.");
        }
        return toResponse(complaint);
    }

    // ── STATUS UPDATE (admin only) ──────────────────────────────────────────

    @Transactional
    public ComplaintResponse updateStatus(Long id, UpdateStatusRequest req, User actor) {
        Complaint complaint = findOrThrow(id);
        String previousStatus = complaint.getStatus().name();

        complaint.setStatus(req.getStatus());

        // Handle assignment change
        if (req.getAssignedToId() != null) {
            User assignee = userRepository.findById(req.getAssignedToId())
                    .orElseThrow(() -> new EntityNotFoundException("Assignee not found"));
            complaint.setAssignedTo(assignee);
        }

        // Set resolvedAt timestamp when marking resolved or closed
        if (req.getStatus() == Complaint.Status.RESOLVED
                || req.getStatus() == Complaint.Status.CLOSED) {
            complaint.setResolvedAt(LocalDateTime.now());
        }

        complaint = complaintRepository.save(complaint);

        // Version snapshot
        recordVersion(complaint, actor, ComplaintVersion.ChangeType.STATUS_CHANGE,
                previousStatus, req.getStatus().name(), req.getReason());

        // Audit log
        recordAudit("COMPLAINT", complaint.getId(), "STATUS_CHANGE", actor.getId(),
                toJson(previousStatus), toJson(req.getStatus().name()));

        log.info("Complaint #{} status changed from {} to {} by {}",
                id, previousStatus, req.getStatus(), actor.getEmail());
        return toResponse(complaint);
    }

    // ── SOFT DELETE ────────────────────────────────────────────────────────

    @Transactional
    public void deleteComplaint(Long id, User actor, boolean isAdmin) {
        Complaint complaint = findOrThrow(id);

        // RBAC: only admin or the original submitter can delete
        if (!isAdmin && !complaint.getSubmittedBy().getId().equals(actor.getId())) {
            throw new AccessDeniedException("You cannot delete this complaint.");
        }

        String snapshot = toJson(complaint);
        complaint.setDeleted(true);
        complaint.setDeletedAt(LocalDateTime.now());
        complaint.setDeletedBy(actor.getId());
        complaintRepository.save(complaint);

        recordVersion(complaint, actor, ComplaintVersion.ChangeType.SOFT_DELETE,
                complaint.getStatus().name(), null, "Complaint deleted");
        recordAudit("COMPLAINT", complaint.getId(), "SOFT_DELETE", actor.getId(),
                snapshot, null);

        log.info("Complaint #{} soft-deleted by {}", id, actor.getEmail());
    }

    // ── DASHBOARD STATS ───────────────────────────────────────────────────

    public DashboardStats getDashboardStats(User actor, boolean isAdmin, long totalUsers) {
        if (isAdmin) {
            return DashboardStats.builder()
                    .total(complaintRepository.count())
                    .submitted(complaintRepository.countByStatus(Complaint.Status.SUBMITTED))
                    .inProgress(complaintRepository.countByStatus(Complaint.Status.IN_PROGRESS))
                    .resolved(complaintRepository.countByStatus(Complaint.Status.RESOLVED))
                    .rejected(complaintRepository.countByStatus(Complaint.Status.REJECTED))
                    .totalUsers(totalUsers)
                    .build();
        } else {
            return DashboardStats.builder()
                    .total(complaintRepository.countBySubmittedBy(actor))
                    .submitted(complaintRepository.countBySubmittedByAndStatus(
                            actor, Complaint.Status.SUBMITTED))
                    .inProgress(complaintRepository.countBySubmittedByAndStatus(
                            actor, Complaint.Status.IN_PROGRESS))
                    .resolved(complaintRepository.countBySubmittedByAndStatus(
                            actor, Complaint.Status.RESOLVED))
                    .rejected(complaintRepository.countBySubmittedByAndStatus(
                            actor, Complaint.Status.REJECTED))
                    .totalUsers(0)
                    .build();
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private Complaint findOrThrow(Long id) {
        return complaintRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Complaint #" + id + " not found or has been deleted."));
    }

    /**
     * Maps a Complaint entity → ComplaintResponse DTO.
     * MENTOR NOTE: This mapping could also live in a dedicated @Component mapper
     * class (MapStruct is the industry tool for this). For now, a private method
     * in the service is fine. When you have 10+ entities, move to MapStruct.
     */
    private ComplaintResponse toResponse(Complaint c) {
        User submitter = c.getSubmittedBy();
        User assignee  = c.getAssignedTo();
        return ComplaintResponse.builder()
                .id(c.getId())
                .subject(c.getSubject())
                .description(c.getDescription())
                .status(c.getStatus().name())
                .priority(c.getPriority().name())
                .category(c.getCategory())
                .submittedById(submitter.getId())
                .submittedByName(submitter.getFirstName() + " " + submitter.getLastName())
                .submittedByEmail(submitter.getEmail())
                .assignedToId(assignee  != null ? assignee.getId() : null)
                .assignedToName(assignee != null
                        ? assignee.getFirstName() + " " + assignee.getLastName() : null)
                .submittedAt(c.getSubmittedAt())
                .updatedAt(c.getUpdatedAt())
                .resolvedAt(c.getResolvedAt())
                .version(c.getVersion())
                .build();
    }

    private void recordVersion(Complaint c, User actor,
                               ComplaintVersion.ChangeType type,
                               String prevStatus, String newStatus, String reason) {
        int nextVersion = versionRepository.countByComplaintId(c.getId()) + 1;

        ComplaintVersion cv = ComplaintVersion.builder()
                .complaintId(c.getId())
                .versionNumber(nextVersion)
                .subject(c.getSubject())
                .description(c.getDescription())
                .status(c.getStatus().name())
                .priority(c.getPriority().name())
                .category(c.getCategory())
                .assignedToId(c.getAssignedTo() != null ? c.getAssignedTo().getId() : null)
                .changedBy(actor)
                .changeType(type)
                .previousStatus(prevStatus)
                .newStatus(newStatus)
                .changeReason(reason)
                .build();

        versionRepository.save(cv);
    }

    private void recordAudit(String entityType, Long entityId, String action,
                             Long actorId, String oldValues, String newValues) {
        AuditLog log = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .performedBy(actorId)
                .oldValues(oldValues)
                .newValues(newValues)
                .build();
        auditLogRepository.save(log);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
