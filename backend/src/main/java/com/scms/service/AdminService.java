package com.scms.service;

import com.scms.dto.AdminDtos.*;
import com.scms.model.Complaint;
import com.scms.model.Role;
import com.scms.model.User;
import com.scms.repository.AuditLogRepository;
import com.scms.repository.ComplaintRepository;
import com.scms.repository.RoleRepository;
import com.scms.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AdminService — business logic for user management and reporting.
 *
 * MENTOR NOTE — Why separate from ComplaintService?
 * Single Responsibility Principle. ComplaintService knows about complaints.
 * AdminService knows about users and analytics. If tomorrow you add
 * "bulk complaint export", it belongs here, not in ComplaintService.
 *
 * MENTOR NOTE — Report data strategy:
 * For a college project, computing stats in Java (loading entities, streaming)
 * is perfectly fine. For production with millions of rows, you'd push these
 * aggregations to the DB layer with native SQL GROUP BY queries or a
 * dedicated analytics DB (ClickHouse, BigQuery). The abstraction boundary
 * (this service) lets you swap the implementation later without touching the
 * controller or the frontend.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository       userRepository;
    private final RoleRepository       roleRepository;
    private final ComplaintRepository  complaintRepository;
    private final AuditLogRepository   auditLogRepository;

    // ═══════════════════════════════════════════════════════════════════════
    //  USER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns all registered users enriched with their complaint counts.
     * The complaint counts are derived from the complaints table using
     * per-user count queries — this avoids loading all complaints into memory.
     */
    public List<UserResponse> listAllUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User #" + id + " not found."));
        return toUserResponse(user);
    }

    /**
     * Toggle a user's active flag. Inactive users cannot log in.
     * Spring Security calls isEnabled() during authentication — if false,
     * DisabledException is thrown and we return 403.
     *
     * MENTOR NOTE — we use reflection / direct field manipulation here
     * because User.active is a final field with @Builder.Default.
     * The cleanest fix is to add a dedicated setter. We use a rebuild
     * pattern: copy the user with a new active value by re-saving.
     */
    @Transactional
    public UserResponse toggleUserStatus(Long id, boolean active, User performedBy) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User #" + id + " not found."));

        // Rebuild user with new active status
        // (active is @Builder.Default final — we update via the setter Lombok generates)
        user.setActive(active);
        userRepository.save(user);

        log.info("Admin {} {} user #{}", performedBy.getEmail(),
                active ? "activated" : "deactivated", id);
        return toUserResponse(user);
    }

    /**
     * Change a user's role. Accepts "USER" or "ADMIN".
     * Replaces all existing roles — a user has exactly one role in SCMS.
     */
    @Transactional
    public UserResponse changeUserRole(Long id, String roleName, User performedBy) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User #" + id + " not found."));

        String upperRole = roleName.toUpperCase();
        Role role = roleRepository.findByName(upperRole)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Role '" + upperRole + "' does not exist. Valid values: USER, ADMIN"));

        user.getRoles().clear();
        user.getRoles().add(role);
        userRepository.save(user);

        log.info("Admin {} changed role of user #{} to {}", performedBy.getEmail(), id, upperRole);
        return toUserResponse(user);
    }

    /**
     * Soft-disable a user by setting active = false.
     * We never hard-delete users because their complaints are linked to them.
     */
    @Transactional
    public void deactivateUser(Long id, User performedBy) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User #" + id + " not found."));
        user.setActive(false);
        userRepository.save(user);
        log.info("Admin {} deactivated user #{}", performedBy.getEmail(), id);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  REPORTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Top-level KPI summary for the report dashboard header.
     *
     * MENTOR NOTE — avg resolution time:
     * We compute it from resolved/closed complaints that have both
     * submittedAt and resolvedAt set. We use Duration.between() and
     * convert to hours. This is the mean; median would be more representative
     * for skewed data — that's a future enhancement.
     */
    public ReportSummary getReportSummary() {
        long total     = complaintRepository.count();
        long submitted = complaintRepository.countByStatus(Complaint.Status.SUBMITTED);
        long inReview  = complaintRepository.countByStatus(Complaint.Status.IN_REVIEW);
        long inProg    = complaintRepository.countByStatus(Complaint.Status.IN_PROGRESS);
        long resolved  = complaintRepository.countByStatus(Complaint.Status.RESOLVED);
        long closed    = complaintRepository.countByStatus(Complaint.Status.CLOSED);
        long rejected  = complaintRepository.countByStatus(Complaint.Status.REJECTED);
        long open      = submitted + inReview + inProg;

        long totalUsers  = userRepository.count();
        long activeUsers = userRepository.findAll().stream()
                .filter(User::isActive).count();

        // Average resolution time (hours) — only from resolved/closed complaints
        List<Complaint> resolvedComplaints = complaintRepository
                .findByStatusOrderBySubmittedAtDesc(Complaint.Status.RESOLVED);
        List<Complaint> closedComplaints = complaintRepository
                .findByStatusOrderBySubmittedAtDesc(Complaint.Status.CLOSED);

        List<Complaint> finishedWithTime = new ArrayList<>(resolvedComplaints);
        finishedWithTime.addAll(closedComplaints);

        double avgHours = finishedWithTime.stream()
                .filter(c -> c.getSubmittedAt() != null && c.getResolvedAt() != null)
                .mapToLong(c -> ChronoUnit.HOURS.between(c.getSubmittedAt(), c.getResolvedAt()))
                .average()
                .orElse(0.0);

        // Month-over-month complaint volume
        LocalDateTime now        = LocalDateTime.now();
        LocalDateTime startMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime startPrev  = startMonth.minusMonths(1);

        long thisMonth = complaintRepository.countBySubmittedAtBetween(startMonth, now);
        long lastMonth = complaintRepository.countBySubmittedAtBetween(startPrev, startMonth);
        double momChange = lastMonth == 0 ? 100.0
                : ((double)(thisMonth - lastMonth) / lastMonth) * 100.0;

        return ReportSummary.builder()
                .totalComplaints(total)
                .openComplaints(open)
                .resolvedComplaints(resolved + closed)
                .rejectedComplaints(rejected)
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .avgResolutionHours(Math.round(avgHours * 10.0) / 10.0)
                .complaintsThisMonth(thisMonth)
                .complaintsLastMonth(lastMonth)
                .monthOverMonthChange(Math.round(momChange * 10.0) / 10.0)
                .build();
    }

    /**
     * Complaint count broken down by status, with percentage share.
     */
    public List<StatusBreakdown> getStatusBreakdown() {
        long total = Math.max(complaintRepository.count(), 1); // prevent div-by-zero

        return Arrays.stream(Complaint.Status.values())
                .map(s -> {
                    long count = complaintRepository.countByStatus(s);
                    return StatusBreakdown.builder()
                            .status(s.name())
                            .count(count)
                            .percentage(Math.round((count * 100.0 / total) * 10.0) / 10.0)
                            .build();
                })
                .filter(b -> b.getCount() > 0)   // omit zero-count statuses
                .sorted(Comparator.comparingLong(StatusBreakdown::getCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Complaint count broken down by category.
     * We load all complaints and group in Java — fine for thousands of rows.
     * For millions, push to a native GROUP BY query.
     */
    public List<CategoryBreakdown> getCategoryBreakdown() {
        List<Complaint> all = complaintRepository.findAllByOrderBySubmittedAtDesc();
        long total = Math.max(all.size(), 1);

        Map<String, Long> grouped = all.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getCategory() != null ? c.getCategory() : "Uncategorized",
                        Collectors.counting()
                ));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> CategoryBreakdown.builder()
                        .category(e.getKey())
                        .count(e.getValue())
                        .percentage(Math.round((e.getValue() * 100.0 / total) * 10.0) / 10.0)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Top 10 users ranked by total complaint count.
     */
    public List<UserComplaintCount> getTopComplainants() {
        return userRepository.findAll().stream()
                .map(u -> {
                    long total    = complaintRepository.countBySubmittedBy(u);
                    long open     = complaintRepository.countBySubmittedByAndStatus(u, Complaint.Status.SUBMITTED)
                            + complaintRepository.countBySubmittedByAndStatus(u, Complaint.Status.IN_PROGRESS)
                            + complaintRepository.countBySubmittedByAndStatus(u, Complaint.Status.IN_REVIEW);
                    long resolved = complaintRepository.countBySubmittedByAndStatus(u, Complaint.Status.RESOLVED);
                    return UserComplaintCount.builder()
                            .userId(u.getId())
                            .userName(u.getFirstName() + " " + u.getLastName())
                            .email(u.getEmail())
                            .total(total)
                            .open(open)
                            .resolved(resolved)
                            .build();
                })
                .filter(u -> u.getTotal() > 0)
                .sorted(Comparator.comparingLong(UserComplaintCount::getTotal).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * Daily complaint counts for the past 30 days.
     * Returns an entry for every day including days with zero complaints
     * so the chart line doesn't have gaps.
     *
     * MENTOR NOTE — Why a complete 30-day series?
     * Chart libraries expect a continuous x-axis. If day 15 had 0 complaints
     * and we skip it, the chart draws a line jump from day 14 to day 16.
     * By generating all 30 dates upfront and filling in counts (defaulting to 0),
     * the chart is smooth.
     */
    public List<DailyCount> getDailyTimeline() {
        LocalDate today = LocalDate.now();
        LocalDate from  = today.minusDays(29);   // 30-day window inclusive

        // Load all complaints in the window
        LocalDateTime windowStart = from.atStartOfDay();
        LocalDateTime windowEnd   = today.plusDays(1).atStartOfDay();

        List<Complaint> inWindow = complaintRepository
                .findBySubmittedAtBetween(windowStart, windowEnd);

        // Group by date
        Map<LocalDate, Long> countByDate = inWindow.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getSubmittedAt().toLocalDate(),
                        Collectors.counting()
                ));

        // Build complete 30-day series
        List<DailyCount> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            result.add(DailyCount.builder()
                    .date(d)
                    .count(countByDate.getOrDefault(d, 0L))
                    .build());
        }
        return result;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private UserResponse toUserResponse(User u) {
        Set<String> roleNames = u.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        long total    = complaintRepository.countBySubmittedBy(u);
        long open     = complaintRepository.countBySubmittedByAndStatus(u, Complaint.Status.SUBMITTED)
                + complaintRepository.countBySubmittedByAndStatus(u, Complaint.Status.IN_PROGRESS)
                + complaintRepository.countBySubmittedByAndStatus(u, Complaint.Status.IN_REVIEW);
        long resolved = complaintRepository.countBySubmittedByAndStatus(u, Complaint.Status.RESOLVED);

        return UserResponse.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .active(u.isActive())
                .roles(roleNames)
                .createdAt(u.getCreatedAt())
                .totalComplaints(total)
                .openComplaints(open)
                .resolvedComplaints(resolved)
                .build();
    }
}
