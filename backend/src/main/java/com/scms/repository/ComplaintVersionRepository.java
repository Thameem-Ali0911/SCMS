package com.scms.repository;

import com.scms.model.AuditLog;
import com.scms.model.ComplaintVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * ComplaintVersionRepository — history of every change to a complaint.
 */
public interface ComplaintVersionRepository extends JpaRepository<ComplaintVersion, Long> {

    /**
     * Full version history for one complaint, oldest-first.
     * Used on the complaint detail page's timeline view.
     */
    List<ComplaintVersion> findByComplaintIdOrderByVersionNumberAsc(Long complaintId);

    /** Latest version number for a given complaint — used to auto-increment. */
    int countByComplaintId(Long complaintId);
}
