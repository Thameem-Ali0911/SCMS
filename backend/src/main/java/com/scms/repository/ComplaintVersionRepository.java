package com.scms.repository;

import com.scms.model.Complaint;
import com.scms.model.ComplaintVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** ComplaintVersionRepository — history of every change to a complaint. */
public interface ComplaintVersionRepository extends JpaRepository<ComplaintVersion, Long> {

    List<ComplaintVersion> findByComplaintOrderByVersionNumberAsc(Complaint complaint);

    int countByComplaint(Complaint complaint);
}
