package com.patientcase.clinical;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SymptomRepository extends JpaRepository<Symptom, Long> {
    List<Symptom> findByEncounterId(Long encounterId);
    void deleteByEncounterId(Long encounterId);
}
