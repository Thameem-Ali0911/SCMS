package com.scms.common;

import com.scms.model.Complaint.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ComplaintStatusPolicyTest — verifies the complaint lifecycle state machine.
 *
 * This is exactly the kind of test the v1.3 report said didn't exist
 * ("Testability: 1/10 — zero tests... you cannot refactor, scale, or
 * deploy this system with confidence"). It needs no Spring context, no
 * database, no mocks — pure business logic, runs in milliseconds.
 */
class ComplaintStatusPolicyTest {

    @ParameterizedTest(name = "{0} -> {1} should be allowed for non-admin")
    @CsvSource({
            "SUBMITTED, IN_REVIEW",
            "SUBMITTED, REJECTED",
            "IN_REVIEW, IN_PROGRESS",
            "IN_REVIEW, REJECTED",
            "IN_PROGRESS, RESOLVED",
            "IN_PROGRESS, REJECTED",
            "RESOLVED, CLOSED",
            "REJECTED, CLOSED"
    })
    void allowsLegalForwardTransitions(Status from, Status to) {
        assertTrue(ComplaintStatusPolicy.isAllowed(from, to, false));
    }

    @ParameterizedTest(name = "{0} -> {1} should be rejected for non-admin")
    @CsvSource({
            "REJECTED, SUBMITTED",
            "RESOLVED, SUBMITTED",
            "RESOLVED, IN_PROGRESS",
            "CLOSED, SUBMITTED",
            "CLOSED, RESOLVED",
            "SUBMITTED, RESOLVED",
            "SUBMITTED, CLOSED"
    })
    void rejectsIllegalTransitions(Status from, Status to) {
        assertFalse(ComplaintStatusPolicy.isAllowed(from, to, false));
    }

    @Test
    void noOpTransitionIsAlwaysAllowed() {
        for (Status s : Status.values()) {
            assertTrue(ComplaintStatusPolicy.isAllowed(s, s, false),
                    "Setting the same status should always be a no-op success: " + s);
        }
    }

    @Test
    void adminCanOverrideMostTransitionsButNotReopenClosed() {
        assertTrue(ComplaintStatusPolicy.isAllowed(Status.REJECTED, Status.SUBMITTED, true),
                "Admin should be able to override the normal state machine");
        assertTrue(ComplaintStatusPolicy.isAllowed(Status.RESOLVED, Status.IN_PROGRESS, true),
                "Admin should be able to reopen a resolved complaint");
        assertFalse(ComplaintStatusPolicy.isAllowed(Status.CLOSED, Status.SUBMITTED, true),
                "CLOSED must remain terminal even for admins, to protect historical integrity");
    }

    @Test
    void closedIsTerminalForEveryone() {
        for (Status target : Status.values()) {
            if (target == Status.CLOSED) continue;
            assertFalse(ComplaintStatusPolicy.isAllowed(Status.CLOSED, target, false));
            assertFalse(ComplaintStatusPolicy.isAllowed(Status.CLOSED, target, true));
        }
    }

    @Test
    void isOpenIdentifiesActiveLifecycleStates() {
        assertTrue(ComplaintStatusPolicy.isOpen(Status.SUBMITTED));
        assertTrue(ComplaintStatusPolicy.isOpen(Status.IN_REVIEW));
        assertTrue(ComplaintStatusPolicy.isOpen(Status.IN_PROGRESS));
        assertFalse(ComplaintStatusPolicy.isOpen(Status.RESOLVED));
        assertFalse(ComplaintStatusPolicy.isOpen(Status.CLOSED));
        assertFalse(ComplaintStatusPolicy.isOpen(Status.REJECTED));
    }
}
