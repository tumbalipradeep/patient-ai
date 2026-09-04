package com.patientcase.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiIntakeSessionRepository extends JpaRepository<AiIntakeSession, Long> {

    /**
     * Find the session for a given encounter.
     * Because of the UNIQUE constraint on encounter_id there is at most one.
     */
    Optional<AiIntakeSession> findByEncounterId(Long encounterId);
}
