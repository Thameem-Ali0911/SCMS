package com.scms.service;

import com.scms.model.Complaint;
import com.scms.repository.ComplaintRepository;
import com.scms.repository.ComplaintRepository.UserStatusCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * ComplaintStatsAggregator — the single shared fix for the v1.3 N+1 query
 * catastrophe.
 *
 * MENTOR NOTE: v1.3's AdminService.listAllUsers() and getTopComplainants()
 * each independently looped over every user and issued 3 separate count
 * queries per user (3N+1 total). Both call sites needed "per-user complaint
 * counts broken down by open/resolved/total" — so instead of fixing each
 * call site separately, this class computes that exact shape with ONE query
 * (ComplaintRepository.countPerUserGroupedByStatus(), a single GROUP BY),
 * and both AdminUserService and AdminReportService now share this one
 * aggregation instead of each re-implementing (and each potentially
 * re-introducing) the N+1 pattern.
 */
@Component
@RequiredArgsConstructor
public class ComplaintStatsAggregator {

    private final ComplaintRepository complaintRepository;

    public record PerUserCounts(long total, long open, long resolved) {
        static final PerUserCounts EMPTY = new PerUserCounts(0, 0, 0);
    }

    /** @return userId → {total, open, resolved} computed from exactly one SQL query. */
    public Map<Long, PerUserCounts> perUserCounts() {
        Map<Long, long[]> raw = new HashMap<>(); // [total, open, resolved]

        for (UserStatusCount row : complaintRepository.countPerUserGroupedByStatus()) {
            long[] counts = raw.computeIfAbsent(row.getUserId(), k -> new long[3]);
            counts[0] += row.getTotal();
            if (isOpenStatus(row.getStatus())) {
                counts[1] += row.getTotal();
            } else if (row.getStatus() == Complaint.Status.RESOLVED) {
                counts[2] += row.getTotal();
            }
        }

        Map<Long, PerUserCounts> result = new HashMap<>();
        raw.forEach((userId, c) -> result.put(userId, new PerUserCounts(c[0], c[1], c[2])));
        return result;
    }

    public PerUserCounts forUser(Map<Long, PerUserCounts> all, Long userId) {
        return all.getOrDefault(userId, PerUserCounts.EMPTY);
    }

    private boolean isOpenStatus(Complaint.Status status) {
        return status == Complaint.Status.SUBMITTED
                || status == Complaint.Status.IN_REVIEW
                || status == Complaint.Status.IN_PROGRESS;
    }
}
