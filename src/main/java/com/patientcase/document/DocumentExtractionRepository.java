package com.patientcase.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentExtractionRepository extends JpaRepository<DocumentExtraction, Long> {

    Optional<DocumentExtraction> findByDocumentId(Long documentId);

    List<DocumentExtraction> findByIntakeIdOrderByCreatedAtDesc(Long intakeId);
}