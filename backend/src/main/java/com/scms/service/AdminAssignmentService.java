package com.scms.service;

import com.scms.common.Roles;
import com.scms.dto.AdminDtos.StaffWorkload;
import com.scms.model.Complaint;
import com.scms.model.User;
import com.scms.repository.ComplaintRepository;
import com.scms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AdminAssignmentService — the data an admin needs to make assignment
 * decisions: who is currently carrying how much load.
 *
 * The actual act of assigning a complaint lives in ComplaintService
 * (assignTo / selfAssign) since that's where the status-transition and
 * audit-trail logic already lives — this service exists specifically for
 * the "who should I assign this to?" question the new Assignments page asks.
 */
@Service
@RequiredArgsConstructor
public class AdminAssignmentService {

    private final UserRepository      userRepository;
    private final ComplaintRepository complaintRepository;

    /**
     * NOTE: this issues a handful of count queries PER staff/admin user, which
     * looks like the v1.3 N+1 pattern but isn't the same scale problem — staff
     * headcount is organisationally bounded (tens of people, not thousands of
     * users/complaints), so this never grows the way listAllUsers() or
     * getTopComplainants() used to. If that assumption ever changes, apply
     * the same ComplaintStatsAggregator pattern used elsewhere.
     */
    public List<StaffWorkload> getStaffWorkload() {
        List<User> staff = userRepository.findActiveByRoleNames(List.of(Roles.STAFF, Roles.ADMIN));

        return staff.stream()
                .map(u -> {
                    long openLoad = complaintRepository.countByAssignedToAndStatus(u, Complaint.Status.IN_REVIEW)
                            + complaintRepository.countByAssignedToAndStatus(u, Complaint.Status.IN_PROGRESS);
                    long resolvedTotal = complaintRepository.countByAssignedToAndStatus(u, Complaint.Status.RESOLVED)
                            + complaintRepository.countByAssignedToAndStatus(u, Complaint.Status.CLOSED);
                    return StaffWorkload.builder()
                            .staffId(u.getId())
                            .staffName(u.getFullName())
                            .email(u.getEmail())
                            .assignedOpen(openLoad)
                            .resolvedTotal(resolvedTotal)
                            .build();
                })
                .sorted((a, b) -> Long.compare(a.getAssignedOpen(), b.getAssignedOpen())) // lightest load first — natural "assign here next" ordering
                .toList();
    }
}
