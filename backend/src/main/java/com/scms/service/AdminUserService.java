package com.scms.service;

import com.scms.common.AuditActions;
import com.scms.common.EntityTypes;
import com.scms.common.HttpRequestUtils;
import com.scms.common.Roles;
import com.scms.dto.AdminDtos.*;
import com.scms.dto.PageResponse;
import com.scms.model.AuditLog;
import com.scms.model.Role;
import com.scms.model.User;
import com.scms.repository.AuditLogRepository;
import com.scms.repository.RoleRepository;
import com.scms.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AdminUserService — user management, split out of the v1.3 god-class
 * AdminService (which the report flagged under Maintainability: "does user
 * management AND report generation AND analytics AND timeline — four
 * distinct responsibilities in one class").
 *
 * CHANGE in v2.0:
 *   • listAllUsers() now uses ComplaintStatsAggregator — ONE query for every
 *     user's complaint counts instead of v1.3's 3N+1 per-user loop.
 *   • changeUserRole() now accepts USER, STAFF, or ADMIN (was USER/ADMIN only).
 *   • Pagination support (Page<UserResponse>) for the admin Users page.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private final UserRepository             userRepository;
    private final RoleRepository             roleRepository;
    private final AuditLogRepository         auditLogRepository;
    private final ComplaintStatsAggregator   statsAggregator;

    public PageResponse<UserResponse> listAllUsers(Pageable pageable, String search) {
        Page<User> page = (search != null && !search.isBlank())
                ? userRepository.search(search.trim(), pageable)
                : userRepository.findAll(pageable);

        // ONE aggregate query for every user's complaint counts — see
        // ComplaintStatsAggregator for the N+1 fix this replaces.
        Map<Long, ComplaintStatsAggregator.PerUserCounts> counts = statsAggregator.perUserCounts();

        return PageResponse.of(page, u -> toUserResponse(u, statsAggregator.forUser(counts, u.getId())));
    }

    public UserResponse getUserById(Long id) {
        User user = findOrThrow(id);
        ComplaintStatsAggregator.PerUserCounts counts =
                statsAggregator.forUser(statsAggregator.perUserCounts(), id);
        return toUserResponse(user, counts);
    }

    @Transactional
    public UserResponse toggleUserStatus(Long id, boolean active, User performedBy) {
        User user = findOrThrow(id);
        user.setActive(active);
        userRepository.save(user);

        recordAudit(id, AuditActions.STATUS_TOGGLE, performedBy.getId(),
                String.valueOf(!active), String.valueOf(active));

        log.info("Admin {} {} user #{}", performedBy.getEmail(), active ? "activated" : "deactivated", id);
        return toUserResponse(user, statsAggregator.forUser(statsAggregator.perUserCounts(), id));
    }

    /** CHANGE in v2.0: now supports USER, STAFF, or ADMIN (was a USER/ADMIN-only toggle). */
    @Transactional
    public UserResponse changeUserRole(Long id, String roleName, User performedBy) {
        User user = findOrThrow(id);

        String upperRole = roleName.toUpperCase();
        if (!Roles.isValid(upperRole)) {
            throw new IllegalArgumentException(
                    "Role '" + upperRole + "' does not exist. Valid values: USER, STAFF, ADMIN");
        }
        Role role = roleRepository.findByName(upperRole)
                .orElseThrow(() -> new IllegalStateException(
                        "Role '" + upperRole + "' is not seeded in the database."));

        String previousRoles = user.getRoles().stream().map(Role::getName).collect(Collectors.joining(","));
        user.getRoles().clear();
        user.getRoles().add(role);
        userRepository.save(user);

        recordAudit(id, AuditActions.ROLE_CHANGE, performedBy.getId(), previousRoles, upperRole);

        log.info("Admin {} changed role of user #{} to {}", performedBy.getEmail(), id, upperRole);
        return toUserResponse(user, statsAggregator.forUser(statsAggregator.perUserCounts(), id));
    }

    @Transactional
    public void deactivateUser(Long id, User performedBy) {
        User user = findOrThrow(id);
        user.setActive(false);
        userRepository.save(user);
        recordAudit(id, AuditActions.STATUS_TOGGLE, performedBy.getId(), "true", "false");
        log.info("Admin {} deactivated user #{}", performedBy.getEmail(), id);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User #" + id + " not found."));
    }

    private void recordAudit(Long userId, String action, Long performedBy, String oldVal, String newVal) {
        auditLogRepository.save(AuditLog.builder()
                .entityType(EntityTypes.USER)
                .entityId(userId)
                .action(action)
                .performedBy(performedBy)
                .oldValues(oldVal)
                .newValues(newVal)
                .ipAddress(HttpRequestUtils.currentIp())
                .userAgent(HttpRequestUtils.currentUserAgent())
                .build());
    }

    private UserResponse toUserResponse(User u, ComplaintStatsAggregator.PerUserCounts counts) {
        Set<String> roleNames = u.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        return UserResponse.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .active(u.isActive())
                .roles(roleNames)
                .createdAt(u.getCreatedAt())
                .totalComplaints(counts.total())
                .openComplaints(counts.open())
                .resolvedComplaints(counts.resolved())
                .build();
    }
}
