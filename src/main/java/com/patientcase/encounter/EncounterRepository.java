package com.patientcase.encounter;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    List<Encounter> findByPatientCaseIdOrderByEncounterDateDesc(Long caseId);

    List<Encounter> findByClinicianIdOrderByEncounterDateDesc(Long clinicianId);

    @Query("SELECT e FROM Encounter e JOIN FETCH e.patientCase pc JOIN FETCH pc.patient ORDER BY e.createdAt DESC")
    List<Encounter> findRecentEncounters(Pageable pageable);

    long countByStatus(EncounterStatus status);

    @Query("SELECT e FROM Encounter e WHERE e.patientCase.id = :caseId ORDER BY e.encounterDate DESC")
    List<Encounter> findByCaseIdOrdered(@Param("caseId") Long caseId);
}
