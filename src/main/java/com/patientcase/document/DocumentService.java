package com.patientcase.document;

import com.patientcase.audit.AuditAction;
import com.patientcase.audit.AuditService;
import com.patientcase.case_management.PatientCaseRepository;
import com.patientcase.case_management.PatientCase;
import com.patientcase.common.ResourceNotFoundException;
import com.patientcase.encounter.EncounterRepository;
import com.patientcase.encounter.Encounter;
import com.patientcase.patient.PatientRepository;
import com.patientcase.patient.Patient;
import com.patientcase.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/gif",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Value("${app.document.storage-path:${user.home}/patientcase-documents}")
    private String storagePath;

    private final DocumentRepository documentRepository;
    private final PatientRepository patientRepository;
    private final PatientCaseRepository caseRepository;
    private final EncounterRepository encounterRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public DocumentService(DocumentRepository documentRepository,
                            PatientRepository patientRepository,
                            PatientCaseRepository caseRepository,
                            EncounterRepository encounterRepository,
                            UserRepository userRepository,
                            AuditService auditService) {
        this.documentRepository = documentRepository;
        this.patientRepository = patientRepository;
        this.caseRepository = caseRepository;
        this.encounterRepository = encounterRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Optional<Document> findById(Long id) {
        return documentRepository.findById(id);
    }

    /**
     * Returns the resolved, validated Path for downloading a document.
     * Throws IllegalArgumentException if the stored path is outside the storage root.
     * Throws ResourceNotFoundException if the file no longer exists on disk.
     */
    public Path resolveDownloadPath(Document document) {
        Path storageDir = Paths.get(storagePath).toAbsolutePath().normalize();
        Path target = Paths.get(document.getStorageReference()).toAbsolutePath().normalize();
        if (!target.startsWith(storageDir)) {
            throw new IllegalArgumentException("Invalid storage reference");
        }
        if (!Files.exists(target)) {
            throw new ResourceNotFoundException("File not found on disk: " + document.getOriginalFilename());
        }
        return target;
    }

    @Transactional(readOnly = true)
    public List<Document> findByPatientId(Long patientId) {
        return documentRepository.findByPatientIdOrderByUploadedAtDesc(patientId);
    }

    @Transactional(readOnly = true)
    public List<Document> findByCaseId(Long caseId) {
        return documentRepository.findByPatientCaseIdOrderByUploadedAtDesc(caseId);
    }

    @Transactional(readOnly = true)
    public List<Document> findAll() {
        return documentRepository.findAll();
    }

    @Transactional
    public Document uploadDocument(MultipartFile file, Long patientId, Long caseId,
                                    Long encounterId, String description, String uploaderUsername) throws IOException {
        validateFile(file);

        Patient patient = patientId == null ? null : patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + patientId));
        PatientCase patientCase = caseId == null ? null : caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + caseId));
        Encounter encounter = encounterId == null ? null : encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + encounterId));
        validateDocumentContext(patient, patientCase, encounter);

        String safeFilename = UUID.randomUUID() + "_" + sanitizeFilename(file.getOriginalFilename());
        Path storageDir = Paths.get(storagePath).toAbsolutePath().normalize();

        if (!Files.exists(storageDir)) {
            Files.createDirectories(storageDir);
        }

        // Prevent path traversal
        Path targetPath = storageDir.resolve(safeFilename).normalize();
        if (!targetPath.startsWith(storageDir)) {
            throw new IllegalArgumentException("Invalid file path");
        }

        Files.copy(file.getInputStream(), targetPath);

        Document document = new Document();
        document.setFilename(safeFilename);
        document.setOriginalFilename(sanitizeFilename(file.getOriginalFilename()));
        document.setContentType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setStorageReference(targetPath.toString());
        document.setDescription(description);

        document.setPatient(patient);
        document.setPatientCase(patientCase);
        document.setEncounter(encounter);

        userRepository.findByUsername(uploaderUsername).ifPresent(document::setUploadedBy);

        Document saved = documentRepository.save(document);
        auditService.log(AuditAction.DOCUMENT_UPLOADED, "Document", saved.getId(),
                "Document uploaded: " + sanitizeFilename(file.getOriginalFilename()));
        return saved;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds maximum size of 10MB");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("File type not allowed: " + file.getContentType());
        }
    }

    private void validateDocumentContext(Patient patient, PatientCase patientCase, Encounter encounter) {
        if (encounter != null) {
            PatientCase encounterCase = encounter.getPatientCase();
            if (patientCase != null && !encounterCase.getId().equals(patientCase.getId())) {
                throw new IllegalArgumentException("The encounter does not belong to the selected case.");
            }
            if (patient != null && !encounterCase.getPatient().getId().equals(patient.getId())) {
                throw new IllegalArgumentException("The encounter does not belong to the selected patient.");
            }
        }
        if (patientCase != null && patient != null && !patientCase.getPatient().getId().equals(patient.getId())) {
            throw new IllegalArgumentException("The case does not belong to the selected patient.");
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "unknown";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
