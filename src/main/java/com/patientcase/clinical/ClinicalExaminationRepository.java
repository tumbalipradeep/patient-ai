package com.patientcase.clinical;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClinicalExaminationRepository extends JpaRepository<ClinicalExamination, Long> {
    List<ClinicalExamination> findByEncounterId(Long encounterId);
    void deleteByEncounterId(Long encounterId);
}
