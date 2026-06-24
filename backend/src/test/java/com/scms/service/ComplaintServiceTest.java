package com.scms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scms.dto.ComplaintDtos.CreateComplaintRequest;
import com.scms.dto.ComplaintDtos.UpdateStatusRequest;
import com.scms.model.*;
import com.scms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ComplaintServiceTest — unit tests for creation, the status-transition
 * state machine enforcement, and the STAFF assignment access-control rules
 * this version adds.
 */
@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private ComplaintVersionRepository versionRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ComplaintService complaintService;

    private User student;
    private User staff;
    private User otherStaff;
    private User admin;
    private Category category;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        complaintService = new ComplaintService(
                complaintRepository, versionRepository, auditLogRepository,
                userRepository, categoryRepository, objectMapper, eventPublisher);

        student = User.builder().id(1L).firstName("Ali").lastName("Student")
                .email("student@scms.com").password("x").roles(roleSet("USER")).build();
        staff = User.builder().id(2L).firstName("Sam").lastName("Staff")
                .email("staff@scms.com").password("x").roles(roleSet("STAFF")).build();
        otherStaff = User.builder().id(3L).firstName("Other").lastName("Staff")
                .email("other-staff@scms.com").password("x").roles(roleSet("STAFF")).build();
        admin = User.builder().id(4L).firstName("System").lastName("Admin")
                .email("admin@scms.com").password("x").roles(roleSet("ADMIN")).build();
        category = Category.builder().id(1L).name("IT & Infrastructure").active(true).build();
    }

    private java.util.Set<Role> roleSet(String name) {
        return java.util.Set.of(Role.builder().id(1).name(name).build());
    }

    private Complaint sampleComplaint(Complaint.Status status, User assignedTo) {
        return Complaint.builder()
                .id(100L)
                .subject("Wifi is down")
                .description("Wifi has been down in the library for two days now.")
                .status(status)
                .priority(Complaint.Priority.MEDIUM)
                .category(category)
                .submittedBy(student)
                .assignedTo(assignedTo)
                .build();
    }

    @Test
    void createComplaint_success_recordsAuditAndPublishesEvent() {
        CreateComplaintRequest req = new CreateComplaintRequest(
                "Wifi is down", "Wifi has been down in the library for two days now.", 1L, "HIGH");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> {
            Complaint c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });
        when(versionRepository.countByComplaint(any())).thenReturn(0);

        var response = complaintService.createComplaint(req, student);

        assertEquals("SUBMITTED", response.getStatus());
        assertEquals("HIGH", response.getPriority());
        assertEquals("IT & Infrastructure", response.getCategory());
        verify(auditLogRepository).save(any(AuditLog.class));
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void createComplaint_inactiveCategory_throwsIllegalArgument() {
        Category inactive = Category.builder().id(2L).name("Old Category").active(false).build();
        CreateComplaintRequest req = new CreateComplaintRequest("Subject here", "A description long enough.", 2L, null);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(inactive));

        assertThrows(IllegalArgumentException.class, () -> complaintService.createComplaint(req, student));
    }

    @Test
    void updateStatus_byUnassignedStaff_throwsAccessDenied() {
        Complaint complaint = sampleComplaint(Complaint.Status.IN_REVIEW, otherStaff);
        when(complaintRepository.findById(100L)).thenReturn(Optional.of(complaint));

        UpdateStatusRequest req = new UpdateStatusRequest(Complaint.Status.IN_PROGRESS, "starting work");

        assertThrows(AccessDeniedException.class,
                () -> complaintService.updateStatus(100L, req, staff, false));
    }

    @Test
    void updateStatus_illegalTransition_throwsIllegalState() {
        Complaint complaint = sampleComplaint(Complaint.Status.SUBMITTED, staff);
        when(complaintRepository.findById(100L)).thenReturn(Optional.of(complaint));

        UpdateStatusRequest req = new UpdateStatusRequest(Complaint.Status.RESOLVED, null); // can't skip straight to RESOLVED

        assertThrows(IllegalStateException.class,
                () -> complaintService.updateStatus(100L, req, staff, false));
    }

    @Test
    void updateStatus_assignedStaff_legalTransition_succeeds() {
        Complaint complaint = sampleComplaint(Complaint.Status.IN_REVIEW, staff);
        when(complaintRepository.findById(100L)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.countByComplaint(any())).thenReturn(1);

        UpdateStatusRequest req = new UpdateStatusRequest(Complaint.Status.IN_PROGRESS, "Investigating");

        var response = complaintService.updateStatus(100L, req, staff, false);

        assertEquals("IN_PROGRESS", response.getStatus());
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void updateStatus_adminCanOverrideIllegalTransition() {
        Complaint complaint = sampleComplaint(Complaint.Status.REJECTED, staff);
        when(complaintRepository.findById(100L)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.countByComplaint(any())).thenReturn(2);

        UpdateStatusRequest req = new UpdateStatusRequest(Complaint.Status.SUBMITTED, "Reopening after appeal");

        var response = complaintService.updateStatus(100L, req, admin, true);

        assertEquals("SUBMITTED", response.getStatus());
    }

    @Test
    void selfAssign_alreadyAssigned_throwsIllegalState() {
        Complaint complaint = sampleComplaint(Complaint.Status.IN_REVIEW, otherStaff);
        when(complaintRepository.findById(100L)).thenReturn(Optional.of(complaint));

        assertThrows(IllegalStateException.class, () -> complaintService.selfAssign(100L, staff));
    }

    @Test
    void selfAssign_unassignedSubmitted_assignsAndMovesToInReview() {
        Complaint complaint = sampleComplaint(Complaint.Status.SUBMITTED, null);
        when(complaintRepository.findById(100L)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.countByComplaint(any())).thenReturn(0);

        var response = complaintService.selfAssign(100L, staff);

        assertEquals(staff.getId(), response.getAssignedToId());
        assertEquals("IN_REVIEW", response.getStatus());
        verify(eventPublisher, atLeastOnce()).publishEvent(any());
    }

    @Test
    void deleteComplaint_byNonOwnerNonAdmin_throwsAccessDenied() {
        Complaint complaint = sampleComplaint(Complaint.Status.SUBMITTED, null);
        when(complaintRepository.findById(100L)).thenReturn(Optional.of(complaint));

        assertThrows(AccessDeniedException.class,
                () -> complaintService.deleteComplaint(100L, staff, false));
    }

    @Test
    void deleteComplaint_byOwner_succeeds() {
        Complaint complaint = sampleComplaint(Complaint.Status.SUBMITTED, null);
        when(complaintRepository.findById(100L)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.countByComplaint(any())).thenReturn(1);

        complaintService.deleteComplaint(100L, student, false);

        assertTrue(complaint.isDeleted());
        verify(auditLogRepository).save(any(AuditLog.class));
    }
}
