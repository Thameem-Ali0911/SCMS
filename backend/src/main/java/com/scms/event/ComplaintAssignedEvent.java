package com.scms.event;

import com.scms.model.Complaint;
import lombok.Getter;

@Getter
public class ComplaintAssignedEvent {
    private final Complaint complaint;

    public ComplaintAssignedEvent(Complaint complaint) {
        this.complaint = complaint;
    }
}
