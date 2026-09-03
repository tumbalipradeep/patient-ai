package com.patientcase.clinical;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {
    List<FollowUp> findByEncounterId(Long encounterId);
    List<FollowUp> findByPatientCaseId(Long caseId);
}
