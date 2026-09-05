package com.patientcase.case_management;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientCaseRepository extends JpaRepository<PatientCase, Long> {

    @Override
    @EntityGraph(attributePaths = "patient")
    Optional<PatientCase> findById(Long id);

    Optional<PatientCase> findByCaseNumber(String caseNumber);

    boolean existsByCaseNumber(String caseNumber);

    List<PatientCase> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    Page<PatientCase> findByPatientId(Long patientId, Pageable pageable);

    long countByStatus(CaseStatus status);

    @Query("SELECT COUNT(pc) FROM PatientCase pc WHERE pc.status IN ('OPEN', 'IN_PROGRESS')")
    long countActiveCases();

    @Query("SELECT pc FROM PatientCase pc JOIN FETCH pc.patient ORDER BY pc.updatedAt DESC")
    List<PatientCase> findRecentCases(Pageable pageable);
}
