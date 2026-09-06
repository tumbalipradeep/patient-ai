package com.patientcase.kiosk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AyushAssessmentRepository extends JpaRepository<AyushAssessment, Long> {

    Optional<AyushAssessment> findByIntakeId(Long intakeId);

    Optional<AyushAssessment> findByEncounterId(Long encounterId);
}