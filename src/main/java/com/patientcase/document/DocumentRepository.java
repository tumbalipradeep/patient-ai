package com.patientcase.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Override
    @EntityGraph(attributePaths = "uploadedBy")
    List<Document> findAll();

    @EntityGraph(attributePaths = "uploadedBy")
    List<Document> findByPatientIdOrderByUploadedAtDesc(Long patientId);

    @EntityGraph(attributePaths = "uploadedBy")
    List<Document> findByPatientCaseIdOrderByUploadedAtDesc(Long caseId);

    List<Document> findByEncounterIdOrderByUploadedAtDesc(Long encounterId);
}
