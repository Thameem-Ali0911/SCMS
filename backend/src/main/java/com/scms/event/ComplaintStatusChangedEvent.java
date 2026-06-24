package com.scms.event;

import com.scms.model.Complaint;
import lombok.Getter;

@Getter
public class ComplaintStatusChangedEvent {
    private final Complaint complaint;
    private final Complaint.Status previousStatus;
    private final Complaint.Status newStatus;

    public ComplaintStatusChangedEvent(Complaint complaint, Complaint.Status previousStatus, Complaint.Status newStatus) {
        this.complaint = complaint;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }
}
