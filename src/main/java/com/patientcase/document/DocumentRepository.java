package com.patientcase.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByPatientIdOrderByUploadedAtDesc(Long patientId);

    List<Document> findByPatientCaseIdOrderByUploadedAtDesc(Long caseId);

    List<Document> findByEncounterIdOrderByUploadedAtDesc(Long encounterId);
}
