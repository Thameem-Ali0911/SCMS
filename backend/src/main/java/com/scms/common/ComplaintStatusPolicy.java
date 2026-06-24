package com.scms.common;

import com.scms.model.Complaint;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * ComplaintStatusPolicy — the complaint lifecycle state machine.
 *
 * MENTOR NOTE — fixing a real bug found during the v1.3 review:
 * The original UpdateStatusRequest accepted ANY Complaint.Status value with
 * no transition validation. A REJECTED complaint could be set back to
 * SUBMITTED, or a RESOLVED complaint could be silently reopened, both of
 * which make no business sense for a grievance system and would corrupt
 * resolution-time metrics (avgResolutionHours assumes resolvedAt is set once
 * and never un-set).
 *
 * This class is the single source of truth for "what transitions are legal".
 * ADMIN is allowed to override this (escalation authority — an admin may
 * need to reopen a wrongly-closed complaint), but STAFF must follow it.
 *
 * Lifecycle:
 *
 *   SUBMITTED ──► IN_REVIEW ──► IN_PROGRESS ──► RESOLVED ──► CLOSED
 *       │              │              │
 *       └──────────────┴──────────────┴────────► REJECTED ──► CLOSED
 *
 * CLOSED and REJECTED→CLOSED are terminal — no further transitions for
 * non-admin actors. DRAFT was removed entirely (see CHANGELOG.md): the v1.3
 * report flagged it as dead code (an enum value the UI never set and no
 * "save as draft" flow ever wrote). Rather than leave an unreachable status
 * in the model, the cleanest fix is removing it — complaints are always
 * created directly as SUBMITTED, matching how the system is actually used.
 */
public final class ComplaintStatusPolicy {

    private ComplaintStatusPolicy() {}

    private static final Map<Complaint.Status, Set<Complaint.Status>> ALLOWED = Map.of(
            Complaint.Status.SUBMITTED,   EnumSet.of(Complaint.Status.IN_REVIEW, Complaint.Status.REJECTED),
            Complaint.Status.IN_REVIEW,   EnumSet.of(Complaint.Status.IN_PROGRESS, Complaint.Status.REJECTED),
            Complaint.Status.IN_PROGRESS, EnumSet.of(Complaint.Status.RESOLVED, Complaint.Status.REJECTED),
            Complaint.Status.RESOLVED,    EnumSet.of(Complaint.Status.CLOSED),
            Complaint.Status.REJECTED,    EnumSet.of(Complaint.Status.CLOSED),
            Complaint.Status.CLOSED,      EnumSet.noneOf(Complaint.Status.class)
    );

    private static final Set<Complaint.Status> TERMINAL =
            EnumSet.of(Complaint.Status.CLOSED);

    /**
     * @param adminOverride true if the actor is an ADMIN — admins may force any
     *                      transition (except out of CLOSED, which is permanently
     *                      terminal even for admins, to protect historical integrity).
     */
    public static boolean isAllowed(Complaint.Status from, Complaint.Status to, boolean adminOverride) {
        if (from == to) return true; // no-op update (e.g. re-saving a reason/assignee) is always fine
        if (TERMINAL.contains(from)) return false; // nobody reopens a CLOSED complaint, not even an admin
        if (adminOverride) return true;
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean isTerminal(Complaint.Status status) {
        return TERMINAL.contains(status);
    }

    public static boolean isOpen(Complaint.Status status) {
        return status == Complaint.Status.SUBMITTED
                || status == Complaint.Status.IN_REVIEW
                || status == Complaint.Status.IN_PROGRESS;
    }
}
