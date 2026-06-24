package com.scms.service;

import com.scms.dto.AdminDtos.*;
import com.scms.model.Complaint;
import com.scms.repository.ComplaintRepository;
import com.scms.repository.ComplaintRepository.CategoryCount;
import com.scms.repository.ComplaintRepository.StatusCount;
import com.scms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AdminReportService — analytics/reporting, split out of the v1.3 god-class
 * AdminService.
 *
 * CHANGE in v2.0 (production hardening) — the N+1 fix, point by point:
 *
 * • getReportSummary() v1.3: 7 separate countByStatus() calls + a full
 * findAll().stream().filter(active) for activeUsers + 2 more findAll() for
 * resolved/closed timing — 12+ queries for one API call. v2.0: ONE
 * countGroupedByStatus() query (status breakdown reused for both summary AND
 * the by-status chart) + countByActiveTrue() (one indexed COUNT query, not a
 * full table load).
 *
 * • getCategoryBreakdown() v1.3: findAllByOrderBySubmittedAtDesc() — loads
 * EVERY complaint in the table into the JVM to group them in Java. v2.0:
 * countGroupedByCategory() — one GROUP BY query, database-side.
 *
 * • getTopComplainants() v1.3: 3N+1 queries (one userRepository.findAll() + 3
 * count queries per user). v2.0: reuses
 * ComplaintStatsAggregator.perUserCounts() — the SAME single query
 * listAllUsers() already needed, computed once and shared.
 *
 * • getDailyTimeline() is left as a bounded 30-day window query — this was
 * never the actual problem v1.3 had (a month of complaints for a college system
 * is not "unbounded"); the methods above were.
 */
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final ComplaintStatsAggregator statsAggregator;

    public ReportSummary getReportSummary() {
        Map<Complaint.Status, Long> byStatus = statusCountMap();

        long submitted = byStatus.getOrDefault(Complaint.Status.SUBMITTED, 0L);
        long inReview = byStatus.getOrDefault(Complaint.Status.IN_REVIEW, 0L);
        long inProg = byStatus.getOrDefault(Complaint.Status.IN_PROGRESS, 0L);
        long resolved = byStatus.getOrDefault(Complaint.Status.RESOLVED, 0L);
        long closed = byStatus.getOrDefault(Complaint.Status.CLOSED, 0L);
        long rejected = byStatus.getOrDefault(Complaint.Status.REJECTED, 0L);
        long open = submitted + inReview + inProg;
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByActiveTrue();

        double avgHours = averageResolutionHours();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime startPrev = startMonth.minusMonths(1);

        long thisMonth = complaintRepository.countBySubmittedAtBetween(startMonth, now);
        long lastMonth = complaintRepository.countBySubmittedAtBetween(startPrev, startMonth);
        double momChange = lastMonth == 0 ? 100.0 : ((double) (thisMonth - lastMonth) / lastMonth) * 100.0;

        long unassigned = complaintRepository.findUnassignedQueue(
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements();

        return ReportSummary.builder()
                .totalComplaints(total)
                .openComplaints(open)
                .resolvedComplaints(resolved + closed)
                .rejectedComplaints(rejected)
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .avgResolutionHours(round1(avgHours))
                .complaintsThisMonth(thisMonth)
                .complaintsLastMonth(lastMonth)
                .monthOverMonthChange(round1(momChange))
                .unassignedComplaints(unassigned)
                .build();
    }

    public List<StatusBreakdown> getStatusBreakdown() {
        Map<Complaint.Status, Long> byStatus = statusCountMap();
        long total = Math.max(byStatus.values().stream().mapToLong(Long::longValue).sum(), 1);

        return byStatus.entrySet().stream()
                .map(e -> StatusBreakdown.builder()
                .status(e.getKey().name())
                .count(e.getValue())
                .percentage(round1(e.getValue() * 100.0 / total))
                .build())
                .sorted(Comparator.comparingLong(StatusBreakdown::getCount).reversed())
                .collect(Collectors.toList());
    }

    public List<CategoryBreakdown> getCategoryBreakdown() {
        List<CategoryCount> rows = complaintRepository.countGroupedByCategory();
        long total = Math.max(rows.stream().mapToLong(CategoryCount::getTotal).sum(), 1);

        return rows.stream()
                .map(r -> CategoryBreakdown.builder()
                .category(r.getCategoryName())
                .count(r.getTotal())
                .percentage(round1(r.getTotal() * 100.0 / total))
                .build())
                .sorted(Comparator.comparingLong(CategoryBreakdown::getCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Top 10 users by complaint volume — reuses the SAME aggregate query
     * listAllUsers() uses.
     */
    public List<UserComplaintCount> getTopComplainants() {
        Map<Long, ComplaintStatsAggregator.PerUserCounts> perUser = statsAggregator.perUserCounts();
        Map<Long, String> namesByUserId = userRepository.findAllById(perUser.keySet()).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getFullName() + "|" + u.getEmail()));

        return perUser.entrySet().stream()
                .map(e -> {
                    String[] nameEmail = namesByUserId.getOrDefault(e.getKey(), "Unknown|unknown").split("\\|", 2);
                    return UserComplaintCount.builder()
                            .userId(e.getKey())
                            .userName(nameEmail[0])
                            .email(nameEmail.length > 1 ? nameEmail[1] : "")
                            .total(e.getValue().total())
                            .open(e.getValue().open())
                            .resolved(e.getValue().resolved())
                            .build();
                })
                .filter(u -> u.getTotal() > 0)
                .sorted(Comparator.comparingLong(UserComplaintCount::getTotal).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * Bounded 30-day window — see class javadoc for why this one was left
     * as-is.
     */
    public List<DailyCount> getDailyTimeline() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(29);

        LocalDateTime windowStart = from.atStartOfDay();
        LocalDateTime windowEnd = today.plusDays(1).atStartOfDay();

        List<Complaint> inWindow = complaintRepository.findBySubmittedAtBetween(windowStart, windowEnd);

        Map<LocalDate, Long> countByDate = inWindow.stream()
                .collect(Collectors.groupingBy(
                        (Complaint c) -> c.getSubmittedAt().toLocalDate(),
                        Collectors.counting()));

        List<DailyCount> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            result.add(DailyCount.builder().date(d).count(countByDate.getOrDefault(d, 0L)).build());
        }
        return result;
    }

    // ── Private helpers ────────────────────────────────────────────────────
    private Map<Complaint.Status, Long> statusCountMap() {
        Map<Complaint.Status, Long> map = new EnumMap<>(Complaint.Status.class);
        for (StatusCount row : complaintRepository.countGroupedByStatus()) {
            map.put(row.getStatus(), row.getTotal());
        }
        return map;
    }

    /**
     * NOTE — documented limitation, not an oversight: this loads every
     * historically resolved/closed complaint into memory to average
     * submittedAt→resolvedAt. Unlike the queries above, this one DOES grow with
     * total lifetime volume, not a bounded window. At meaningful scale (tens of
     * thousands of resolved complaints), replace with a native query
     * (`AVG(TIMESTAMPDIFF(HOUR, submitted_at, resolved_at))`) — left as JPQL
     * here because cross-database datetime-diff functions aren't portable, and
     * a native query would tie this method to MySQL syntax. Tracked in
     * README.md's roadmap section.
     */
    private double averageResolutionHours() {
        List<Complaint> resolved = complaintRepository.findByStatusOrderBySubmittedAtDesc(Complaint.Status.RESOLVED);
        List<Complaint> closed = complaintRepository.findByStatusOrderBySubmittedAtDesc(Complaint.Status.CLOSED);

        List<Complaint> finished = new ArrayList<>(resolved);
        finished.addAll(closed);

        return finished.stream()
                .filter(c -> c.getSubmittedAt() != null && c.getResolvedAt() != null)
                .mapToLong(c -> ChronoUnit.HOURS.between(c.getSubmittedAt(), c.getResolvedAt()))
                .average()
                .orElse(0.0);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
