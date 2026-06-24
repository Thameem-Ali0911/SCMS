package com.scms.notification;

import com.scms.event.ComplaintAssignedEvent;
import com.scms.event.ComplaintCreatedEvent;
import com.scms.event.ComplaintStatusChangedEvent;
import com.scms.model.Complaint;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

/**
 * NotificationListener — reacts to complaint lifecycle events by sending
 * email notifications, fully decoupled from ComplaintService.
 *
 * @TransactionalEventListener(phase = AFTER_COMMIT) means this only runs if
 * the database transaction that published the event actually succeeded —
 * if creating a complaint rolls back for any reason, no "your complaint was
 * received" email is sent for a complaint that doesn't exist.
 *
 * @Async (backed by the "notificationExecutor" bean — see AsyncConfig) means
 * a slow or unreachable SMTP server adds zero latency to the original HTTP
 * request; the user's "create complaint" call returns as soon as the DB
 * commit succeeds, never waiting on mail delivery.
 */
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final EmailSender emailSender;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onComplaintCreated(ComplaintCreatedEvent event) {
        Complaint c = event.getComplaint();
        emailSender.send(
                c.getSubmittedBy().getEmail(),
                "We've received your complaint — #" + c.getId(),
                "<p>Hi " + escape(c.getSubmittedBy().getFirstName()) + ",</p>" +
                "<p>Your complaint <strong>\"" + escape(c.getSubject()) + "\"</strong> " +
                "has been received and logged under reference <strong>#" + c.getId() + "</strong>.</p>" +
                "<p>Current status: <strong>" + c.getStatus() + "</strong>.</p>" +
                "<p>You can track its progress from your SCMS dashboard.</p>"
        );
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(ComplaintStatusChangedEvent event) {
        Complaint c = event.getComplaint();
        emailSender.send(
                c.getSubmittedBy().getEmail(),
                "Update on your complaint — #" + c.getId(),
                "<p>Hi " + escape(c.getSubmittedBy().getFirstName()) + ",</p>" +
                "<p>Your complaint <strong>\"" + escape(c.getSubject()) + "\"</strong> " +
                "(#" + c.getId() + ") changed status:</p>" +
                "<p><strong>" + event.getPreviousStatus() + "</strong> &rarr; <strong>" + event.getNewStatus() + "</strong></p>"
        );
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssigned(ComplaintAssignedEvent event) {
        Complaint c = event.getComplaint();
        if (c.getAssignedTo() == null) return;
        emailSender.send(
                c.getAssignedTo().getEmail(),
                "Complaint assigned to you — #" + c.getId(),
                "<p>Hi " + escape(c.getAssignedTo().getFirstName()) + ",</p>" +
                "<p>Complaint <strong>\"" + escape(c.getSubject()) + "\"</strong> (#" + c.getId() + ") " +
                "has been assigned to you for resolution. Priority: <strong>" + c.getPriority() + "</strong>.</p>"
        );
    }

    /** Minimal HTML-escaping — these strings are interpolated into an HTML email body. */
    private String escape(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
