package com.scms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scms.common.AuditActions;
import com.scms.common.ComplaintStatusPolicy;
import com.scms.common.EntityTypes;
import com.scms.common.HttpRequestUtils;
import com.scms.dto.ComplaintDtos.*;
import com.scms.event.ComplaintAssignedEvent;
import com.scms.event.ComplaintCreatedEvent;
import com.scms.event.ComplaintStatusChangedEvent;
import com.scms.model.*;
import com.scms.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ComplaintService — core business logic for every complaint operation.
 *
 * CHANGE in v2.0 (production hardening) — see CHANGELOG.md for the full list.
 * Highlights relevant to this class specifically:
 *
 *   • Category is now a real FK lookup (CategoryRepository), not a free-text
 *     field copied verbatim from the request.
 *
 *   • Every list method returns Page<ComplaintResponse> — see PageResponse
 *     for why (v1.3's unpaginated findAll() was flagged as a production
 *     blocker: "GET /api/complaints will return 50,000 rows... the browser
 *     will freeze").
 *
 *   • Status transitions go through ComplaintStatusPolicy. STAFF must follow
 *     the legal state machine; ADMIN may override (escalation authority).
 *     v1.3 accepted ANY status value with no validation at all.
 *
 *   • selfAssign() / assignTo() implement the assignment workflow this
 *     version adds: a STAFF (or ADMIN) user claims an unassigned complaint
 *     from the queue, or an ADMIN assigns it to a specific staff member —
 *     see AdminAssignmentService for the admin-initiated path.
 *
 *   • Side effects (audit-log + email notification) are split by how
 *     strict their consistency requirement is: the ComplaintVersion +
 *     AuditLog writes stay INSIDE the same @Transactional boundary as the
 *     complaint write itself (a grievance system's audit trail must be as
 *     strongly consistent as the record it describes). Email notifications
 *     are published as Spring application events and handled by
 *     NotificationListener AFTER the transaction commits — see that class
 *     for why that split is the architecturally correct compromise, not an
 *     inconsistency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintService {

    private final ComplaintRepository        complaintRepository;
    private final ComplaintVersionRepository versionRepository;
    private final AuditLogRepository         auditLogRepository;
    private final UserRepository             userRepository;
    private final CategoryRepository         categoryRepository;
    private final ObjectMapper               objectMapper;
    private final ApplicationEventPublisher  eventPublisher;

    // ── CREATE ──────────────────────────────────────────────────────────────

    @Transactional
    public ComplaintResponse createComplaint(CreateComplaintRequest req, User actor) {
        Complaint.Priority priority = parsePriority(req.getPriority());

        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));
        if (!category.isActive()) {
            throw new IllegalArgumentException("This category is no longer accepting new complaints.");
        }

        Complaint complaint = Complaint.builder()
                .subject(req.getSubject().trim())
                .description(req.getDescription().trim())
                .category(category)
                .priority(priority)
                .status(Complaint.Status.SUBMITTED)
                .submittedBy(actor)
                .build();

        complaint = complaintRepository.save(complaint);

        recordVersion(complaint, actor, ComplaintVersion.ChangeType.CREATE,
                null, Complaint.Status.SUBMITTED.name(), "Complaint submitted");
        recordAudit(EntityTypes.COMPLAINT, complaint.getId(), AuditActions.CREATE, actor.getId(),
                null, toJson(complaint));

        eventPublisher.publishEvent(new ComplaintCreatedEvent(complaint));

        log.info("Complaint #{} created by {}", complaint.getId(), actor.getEmail());
        return toResponse(complaint);
    }

    // ── READ — paginated lists ──────────────────────────────────────────────

    public Page<ComplaintResponse> getMyComplaints(User actor, Pageable pageable, Complaint.Status status) {
        Page<Complaint> page = status != null
                ? complaintRepository.findBySubmittedByAndStatus(actor, status, pageable)
                : complaintRepository.findBySubmittedBy(actor, pageable);
        return page.map(this::toResponse);
    }

    /** Admin-only — every complaint in the system, optionally filtered. */
    public Page<ComplaintResponse> getAllComplaints(Pageable pageable, Complaint.Status status, String search) {
        Page<Complaint> page;
        boolean hasSearch = search != null && !search.isBlank();
        if (status != null && hasSearch) {
            page = complaintRepository.searchByStatus(status, search.trim(), pageable);
        } else if (status != null) {
            page = complaintRepository.findByStatus(status, pageable);
        } else if (hasSearch) {
            page = complaintRepository.search(search.trim(), pageable);
        } else {
            page = complaintRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    /** Staff/admin — complaints currently assigned to the calling user. */
    public Page<ComplaintResponse> getMyQueue(User actor, Pageable pageable, Complaint.Status status) {
        Page<Complaint> page = status != null
                ? complaintRepository.findByAssignedToAndStatus(actor, status, pageable)
                : complaintRepository.findByAssignedTo(actor, pageable);
        return page.map(this::toResponse);
    }

    /** Staff/admin — the unassigned pick-up queue, priority-ordered. */
    public Page<ComplaintResponse> getUnassignedQueue(Pageable pageable) {
        return complaintRepository.findUnassignedQueue(pageable).map(this::toResponse);
    }

    // ── READ — single complaint (access-controlled) ────────────────────────

    public ComplaintResponse getComplaint(Long id, User actor) {
        Complaint complaint = findOrThrow(id);
        assertCanView(complaint, actor);
        return toResponse(complaint);
    }

    public ComplaintHistoryResponse getHistory(Long id, User actor) {
        Complaint complaint = findOrThrow(id);
        assertCanView(complaint, actor);

        List<ComplaintVersionEntry> entries = versionRepository
                .findByComplaintOrderByVersionNumberAsc(complaint)
                .stream()
                .map(v -> ComplaintVersionEntry.builder()
                        .versionNumber(v.getVersionNumber())
                        .changeType(v.getChangeType() != null ? v.getChangeType().name() : null)
                        .previousStatus(v.getPreviousStatus())
                        .newStatus(v.getNewStatus())
                        .changedByName(v.getChangedBy() != null ? v.getChangedBy().getFullName() : "System")
                        .changeReason(v.getChangeReason())
                        .changedAt(v.getChangedAt())
                        .build())
                .toList();

        return ComplaintHistoryResponse.builder()
                .complaintId(complaint.getId())
                .versions(entries)
                .build();
    }

    // ── STATUS UPDATE ────────────────────────────────────────────────────────

    @Transactional
    public ComplaintResponse updateStatus(Long id, UpdateStatusRequest req, User actor, boolean isAdmin) {
        Complaint complaint = findOrThrow(id);
        assertCanManage(complaint, actor, isAdmin);

        Complaint.Status previous = complaint.getStatus();
        Complaint.Status target   = req.getStatus();

        if (!ComplaintStatusPolicy.isAllowed(previous, target, isAdmin)) {
            throw new IllegalStateException(
                    "Cannot move a complaint from " + previous + " to " + target + ".");
        }

        complaint.setStatus(target);
        if ((target == Complaint.Status.RESOLVED || target == Complaint.Status.CLOSED)
                && complaint.getResolvedAt() == null) {
            complaint.setResolvedAt(LocalDateTime.now());
        }

        complaint = complaintRepository.save(complaint);

        recordVersion(complaint, actor, ComplaintVersion.ChangeType.STATUS_CHANGE,
                previous.name(), target.name(), req.getReason());
        recordAudit(EntityTypes.COMPLAINT, complaint.getId(), AuditActions.STATUS_CHANGE, actor.getId(),
                toJson(previous.name()), toJson(target.name()));

        if (previous != target) {
            eventPublisher.publishEvent(new ComplaintStatusChangedEvent(complaint, previous, target));
        }

        log.info("Complaint #{} status changed from {} to {} by {}", id, previous, target, actor.getEmail());
        return toResponse(complaint);
    }

    // ── ASSIGNMENT WORKFLOW ──────────────────────────────────────────────────

    /** A STAFF (or ADMIN) user claims an unassigned complaint from the queue. */
    @Transactional
    public ComplaintResponse selfAssign(Long id, User actor) {
        Complaint complaint = findOrThrow(id);
        if (complaint.getAssignedTo() != null) {
            throw new IllegalStateException("This complaint is already assigned to "
                    + complaint.getAssignedTo().getFullName() + ".");
        }
        return doAssign(complaint, actor, actor);
    }

    /** Admin assigns a complaint to a specific staff/admin user. */
    @Transactional
    public ComplaintResponse assignTo(Long id, Long assigneeId, User actor) {
        Complaint complaint = findOrThrow(id);
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new EntityNotFoundException("Assignee not found"));
        if (!assignee.hasRole("STAFF") && !assignee.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Complaints can only be assigned to STAFF or ADMIN users.");
        }
        return doAssign(complaint, assignee, actor);
    }

    private ComplaintResponse doAssign(Complaint complaint, User assignee, User actor) {
        Complaint.Status previous = complaint.getStatus();
        complaint.setAssignedTo(assignee);
        if (previous == Complaint.Status.SUBMITTED) {
            complaint.setStatus(Complaint.Status.IN_REVIEW);
        }
        complaint = complaintRepository.save(complaint);

        recordVersion(complaint, actor, ComplaintVersion.ChangeType.ASSIGN,
                previous.name(), complaint.getStatus().name(),
                "Assigned to " + assignee.getFullName());
        recordAudit(EntityTypes.COMPLAINT, complaint.getId(), AuditActions.ASSIGN, actor.getId(),
                null, toJson(assignee.getId()));

        eventPublisher.publishEvent(new ComplaintAssignedEvent(complaint));
        if (previous != complaint.getStatus()) {
            eventPublisher.publishEvent(new ComplaintStatusChangedEvent(complaint, previous, complaint.getStatus()));
        }

        log.info("Complaint #{} assigned to {} by {}", complaint.getId(), assignee.getEmail(), actor.getEmail());
        return toResponse(complaint);
    }

    // ── SOFT DELETE ────────────────────────────────────────────────────────

    @Transactional
    public void deleteComplaint(Long id, User actor, boolean isAdmin) {
        Complaint complaint = findOrThrow(id);

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
        recordAudit(EntityTypes.COMPLAINT, complaint.getId(), AuditActions.SOFT_DELETE, actor.getId(),
                snapshot, null);

        log.info("Complaint #{} soft-deleted by {}", id, actor.getEmail());
    }

    // ── DASHBOARD STATS ───────────────────────────────────────────────────

    public DashboardStats getDashboardStats(User actor, boolean isAdmin, boolean isStaff, long totalUsers) {
        if (isAdmin) {
            return DashboardStats.builder()
                    .total(complaintRepository.count())
                    .submitted(complaintRepository.countByStatus(Complaint.Status.SUBMITTED))
                    .inProgress(complaintRepository.countByStatus(Complaint.Status.IN_PROGRESS))
                    .resolved(complaintRepository.countByStatus(Complaint.Status.RESOLVED))
                    .rejected(complaintRepository.countByStatus(Complaint.Status.REJECTED))
                    .totalUsers(totalUsers)
                    .build();
        }
        if (isStaff) {
            long myOpen = complaintRepository.countByAssignedToAndStatus(actor, Complaint.Status.IN_REVIEW)
                    + complaintRepository.countByAssignedToAndStatus(actor, Complaint.Status.IN_PROGRESS);
            return DashboardStats.builder()
                    .total(complaintRepository.countByAssignedTo(actor))
                    .submitted(0)
                    .inProgress(complaintRepository.countByAssignedToAndStatus(actor, Complaint.Status.IN_PROGRESS))
                    .resolved(complaintRepository.countByAssignedToAndStatus(actor, Complaint.Status.RESOLVED))
                    .rejected(complaintRepository.countByAssignedToAndStatus(actor, Complaint.Status.REJECTED))
                    .myQueueCount(myOpen)
                    .unassignedCount(complaintRepository.findUnassignedQueue(
                            org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                    .build();
        }
        return DashboardStats.builder()
                .total(complaintRepository.countBySubmittedBy(actor))
                .submitted(complaintRepository.countBySubmittedByAndStatus(actor, Complaint.Status.SUBMITTED))
                .inProgress(complaintRepository.countBySubmittedByAndStatus(actor, Complaint.Status.IN_PROGRESS))
                .resolved(complaintRepository.countBySubmittedByAndStatus(actor, Complaint.Status.RESOLVED))
                .rejected(complaintRepository.countBySubmittedByAndStatus(actor, Complaint.Status.REJECTED))
                .totalUsers(0)
                .build();
    }

    // ── Access control helpers ──────────────────────────────────────────────

    private void assertCanView(Complaint complaint, User actor) {
        boolean isOwner    = complaint.getSubmittedBy().getId().equals(actor.getId());
        boolean isAssignee = complaint.getAssignedTo() != null
                && complaint.getAssignedTo().getId().equals(actor.getId());
        boolean isStaffPreviewingUnassigned = actor.hasRole("STAFF") && complaint.getAssignedTo() == null;
        boolean isPrivileged = actor.hasRole("ADMIN");

        if (!(isOwner || isAssignee || isStaffPreviewingUnassigned || isPrivileged)) {
            throw new AccessDeniedException("You do not have access to this complaint.");
        }
    }

    private void assertCanManage(Complaint complaint, User actor, boolean isAdmin) {
        if (isAdmin) return;
        boolean isAssignee = complaint.getAssignedTo() != null
                && complaint.getAssignedTo().getId().equals(actor.getId());
        if (!isAssignee) {
            throw new AccessDeniedException(
                    "Only the staff member this complaint is assigned to (or an admin) can update its status.");
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private Complaint.Priority parsePriority(String raw) {
        if (raw == null) return Complaint.Priority.MEDIUM;
        try {
            return Complaint.Priority.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Complaint.Priority.MEDIUM;
        }
    }

    private Complaint findOrThrow(Long id) {
        return complaintRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Complaint #" + id + " not found or has been deleted."));
    }

    private ComplaintResponse toResponse(Complaint c) {
        User submitter = c.getSubmittedBy();
        User assignee  = c.getAssignedTo();
        return ComplaintResponse.builder()
                .id(c.getId())
                .subject(c.getSubject())
                .description(c.getDescription())
                .status(c.getStatus().name())
                .priority(c.getPriority().name())
                .category(c.getCategory().getName())
                .categoryId(c.getCategory().getId())
                .submittedById(submitter.getId())
                .submittedByName(submitter.getFullName())
                .submittedByEmail(submitter.getEmail())
                .assignedToId(assignee != null ? assignee.getId() : null)
                .assignedToName(assignee != null ? assignee.getFullName() : null)
                .submittedAt(c.getSubmittedAt())
                .updatedAt(c.getUpdatedAt())
                .resolvedAt(c.getResolvedAt())
                .version(c.getVersion())
                .build();
    }

    private void recordVersion(Complaint c, User actor,
                               ComplaintVersion.ChangeType type,
                               String prevStatus, String newStatus, String reason) {
        int nextVersion = versionRepository.countByComplaint(c) + 1;

        ComplaintVersion cv = ComplaintVersion.builder()
                .complaint(c)
                .versionNumber(nextVersion)
                .subject(c.getSubject())
                .description(c.getDescription())
                .status(c.getStatus().name())
                .priority(c.getPriority().name())
                .category(c.getCategory().getName())
                .assignedTo(c.getAssignedTo())
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
        AuditLog entry = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .performedBy(actorId)
                .oldValues(oldValues)
                .newValues(newValues)
                .ipAddress(HttpRequestUtils.currentIp())
                .userAgent(HttpRequestUtils.currentUserAgent())
                .build();
        auditLogRepository.save(entry);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialise audit payload for logging: {}", e.getMessage());
            return "{\"_serializationError\":true}";
        }
    }
}
