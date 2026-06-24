package com.scms.event;

import com.scms.model.Complaint;
import lombok.Getter;

/**
 * ComplaintCreatedEvent — published after a complaint is successfully
 * committed to the database, consumed by NotificationListener to send a
 * "we received your complaint" email asynchronously.
 *
 * MENTOR NOTE — why an event instead of calling NotificationService directly:
 * v1.3's Software Architect review flagged exactly this: "all cross-cutting
 * concerns are synchronous and inside @Transactional boundaries... these
 * should be eventual-consistency events." Publishing an event and listening
 * with @TransactionalEventListener(phase = AFTER_COMMIT) means: (a) email
 * sending can never roll back the complaint creation if SMTP is briefly
 * down, and (b) adding a NEW side effect later (SMS, push notification,
 * webhook to a ticketing system) means adding a new listener — zero changes
 * to ComplaintService. That is the Open/Closed Principle the v1.3 report
 * said was missing here.
 */
@Getter
public class ComplaintCreatedEvent {
    private final Complaint complaint;

    public ComplaintCreatedEvent(Complaint complaint) {
        this.complaint = complaint;
    }
}
