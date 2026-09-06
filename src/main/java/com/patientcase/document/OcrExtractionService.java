package com.patientcase.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientcase.document.ocr.OcrProvider;
import com.patientcase.kiosk.KioskIntake;
import com.patientcase.kiosk.KioskIntakeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates digitization/intelligence of uploaded medical documents.
 *
 * Uses the configured {@link OcrProvider} behind a clean abstraction. When no
 * real OCR engine is available, the record honestly reports UNSUPPORTED rather
 * than fabricating extracted values.
 *
 * Extracted intelligence is a physician-attention aid, never a definitive
 * clinical reading. Raw document content is never logged.
 */
@Service
public class OcrExtractionService {

    private static final Logger log = LoggerFactory.getLogger(OcrExtractionService.class);

    private final DocumentExtractionRepository extractionRepository;
    private final DocumentRepository documentRepository;
    private final KioskIntakeRepository intakeRepository;
    private final OcrProvider ocrProvider;
    private final ObjectMapper objectMapper;

    @Value("${app.document.storage-path:${user.home}/patientcase-documents}")
    private String storagePath;

    public OcrExtractionService(DocumentExtractionRepository extractionRepository,
                                DocumentRepository documentRepository,
                                KioskIntakeRepository intakeRepository,
                                OcrProvider ocrProvider,
                                ObjectMapper objectMapper) {
        this.extractionRepository = extractionRepository;
        this.documentRepository = documentRepository;
        this.intakeRepository = intakeRepository;
        this.ocrProvider = ocrProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * Request digitization of a document. Safe default behaviour when no
     * provider is available: record UNSUPPORTED so the UI shows an honest
     * "digitization unavailable" state instead of claiming OCR occurred.
     *
     * @param documentId the uploaded document
     * @param intakeId   the intake the document was uploaded against (optional)
     */
    @Transactional
    public DocumentExtraction requestExtraction(Long documentId, Long intakeId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        KioskIntake intake = intakeId == null ? null
                : intakeRepository.findById(intakeId).orElse(null);

        DocumentExtraction extraction = extractionRepository.findByDocumentId(documentId)
                .orElseGet(() -> {
                    DocumentExtraction ex = new DocumentExtraction();
                    ex.setDocument(document);
                    ex.setIntake(intake);
                    return ex;
                });

        if (!ocrProvider.isAvailable()) {
            extraction.setStatus(DocumentExtraction.Status.UNSUPPORTED);
            extraction.setProvider(ocrProvider.providerId());
            extraction.setExtractedJson(null);
            log.info("Document digitization unavailable; record {} marked UNSUPPORTED", documentId);
            return extractionRepository.save(extraction);
        }

        extraction.setStatus(DocumentExtraction.Status.PROCESSING);
        extraction.setProvider(ocrProvider.providerId());
        extractionRepository.save(extraction);

        try {
            byte[] content = readStorage(document);
            OcrProvider.DocumentIntelligence intelligence =
                    ocrProvider.extract(content, document.getContentType(), document.getOriginalFilename());

            if (!intelligence.isProcessed()) {
                extraction.setStatus(DocumentExtraction.Status.FAILED);
                extraction.setErrorMessage(intelligence.getError());
                extraSafeSave(extraction, intelligence, intakeId);
                return extraction;
            }

            extraction.setStatus(DocumentExtraction.Status.COMPLETED);
            extraction.setErrorMessage(null);
            extraction.setExtractedJson(toJson(intelligence));
            extraSafeSave(extraction, intelligence, intakeId);
            return extraction;
        } catch (Exception e) {
            log.warn("Document extraction failed for document {}: {}",
                    documentId, e.getClass().getSimpleName());
            extraction.setStatus(DocumentExtraction.Status.FAILED);
            extraction.setErrorMessage("Document intelligence could not be generated for this file.");
            return extractionRepository.save(extraction);
        }
    }

    private void extraSafeSave(DocumentExtraction extraction,
                               OcrProvider.DocumentIntelligence intelligence,
                               Long intakeId) {
        if (intakeId != null && extraction.getIntake() == null) {
            intakeRepository.findById(intakeId).ifPresent(extraction::setIntake);
        }
        extractionRepository.save(extraction);
    }

    private byte[] readStorage(Document document) {
        Path storageDir = Paths.get(storagePath).toAbsolutePath().normalize();
        Path target = Paths.get(document.getStorageReference()).toAbsolutePath().normalize();
        if (!target.startsWith(storageDir)) {
            throw new IllegalArgumentException("Invalid storage reference");
        }
        if (!Files.exists(target)) {
            throw new IllegalArgumentException("File not found on disk");
        }
        try {
            return Files.readAllBytes(target);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("File could not be read from storage", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<DocumentExtraction> findByDocumentId(Long documentId) {
        return extractionRepository.findByDocumentId(documentId);
    }

    @Transactional(readOnly = true)
    public List<DocumentExtraction> findByIntakeId(Long intakeId) {
        return extractionRepository.findByIntakeIdOrderByCreatedAtDesc(intakeId);
    }

    @Transactional(readOnly = true)
    public boolean isDigitizationAvailable() {
        return ocrProvider.isAvailable();
    }

    @Transactional(readOnly = true)
    public String providerId() {
        return ocrProvider.providerId();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialise extraction result: {}", e.getClass().getSimpleName());
            return "{}";
        }
    }
}