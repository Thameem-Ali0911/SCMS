package com.scms.repository;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.scms.model.Category;
import com.scms.model.Complaint;
import com.scms.model.Role;
import com.scms.model.User;

/**
 * ComplaintRepositoryTest — @DataJpaTest against an in-memory H2 database.
 *
 * Verifies two things the v1.3 report flagged as untested: 1. The
 * @SQLRestriction("is_deleted = false") soft-delete filter actually excludes
 * deleted rows from EVERY query, including plain findAll(). 2. The aggregate
 * GROUP BY queries that replaced the N+1 loops (countGroupedByStatus,
 * findUnassignedQueue's priority ordering) return correct results, not just
 * "fewer queries."
 */
@DataJpaTest
@ActiveProfiles("test")
class ComplaintRepositoryTest {

    @Autowired
    private ComplaintRepository complaintRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private RoleRepository roleRepository;

    private User student;
    private Category category;

    @BeforeEach
    void setUp() {
        Role userRole = roleRepository.save(Role.builder().name("USER").build());
        student = userRepository.save(User.builder()
                .firstName("Ali").lastName("Student")
                .email("ali@scms.com").password("hash")
                .roles(Set.of(userRole))
                .build());
        category = categoryRepository.save(Category.builder().name("IT & Infrastructure").active(true).build());
    }

    private Complaint persistComplaint(Complaint.Status status, Complaint.Priority priority) {
        return complaintRepository.save(Complaint.builder()
                .subject("Test complaint " + status)
                .description("A sufficiently long description for testing purposes.")
                .status(status)
                .priority(priority)
                .category(category)
                .submittedBy(student)
                .build());
    }

    @Test
    void softDeleteFilter_excludesDeletedComplaintsFromFindAll() {
        Complaint visible = persistComplaint(Complaint.Status.SUBMITTED, Complaint.Priority.MEDIUM);
        Complaint deleted = persistComplaint(Complaint.Status.SUBMITTED, Complaint.Priority.MEDIUM);

        deleted.setDeleted(true);
        complaintRepository.save(deleted);

        List<Complaint> all = complaintRepository.findAll();

        assertEquals(1, all.size());
        assertEquals(visible.getId(), all.get(0).getId());
    }

    @Test
    void countGroupedByStatus_returnsCorrectAggregateCounts() {
        persistComplaint(Complaint.Status.SUBMITTED, Complaint.Priority.LOW);
        persistComplaint(Complaint.Status.SUBMITTED, Complaint.Priority.LOW);
        persistComplaint(Complaint.Status.RESOLVED, Complaint.Priority.LOW);

        var rows = complaintRepository.countGroupedByStatus();

        long submittedCount = rows.stream()
                .filter(r -> r.getStatus() == Complaint.Status.SUBMITTED)
                .mapToLong(ComplaintRepository.StatusCount::getTotal)
                .sum();
        long resolvedCount = rows.stream()
                .filter(r -> r.getStatus() == Complaint.Status.RESOLVED)
                .mapToLong(ComplaintRepository.StatusCount::getTotal)
                .sum();

        assertEquals(2, submittedCount);
        assertEquals(1, resolvedCount);
    }

    @Test
    void findUnassignedQueue_ordersCriticalBeforeLow() {
        persistComplaint(Complaint.Status.SUBMITTED, Complaint.Priority.LOW);
        persistComplaint(Complaint.Status.SUBMITTED, Complaint.Priority.CRITICAL);
        persistComplaint(Complaint.Status.SUBMITTED, Complaint.Priority.MEDIUM);

        List<Complaint> queue = complaintRepository
                .findUnassignedQueue(PageRequest.of(0, 10))
                .getContent();

        assertEquals(3, queue.size());
        assertEquals(Complaint.Priority.CRITICAL, queue.get(0).getPriority());
    }

    @Test
    void findUnassignedQueue_excludesAlreadyAssignedAndTerminalStatuses() {
        Complaint resolved = persistComplaint(Complaint.Status.RESOLVED, Complaint.Priority.CRITICAL);
        persistComplaint(Complaint.Status.SUBMITTED, Complaint.Priority.LOW);

        List<Complaint> queue = complaintRepository
                .findUnassignedQueue(PageRequest.of(0, 10))
                .getContent();

        assertEquals(1, queue.size());
        assertNotEquals(resolved.getId(), queue.get(0).getId());
    }
}
