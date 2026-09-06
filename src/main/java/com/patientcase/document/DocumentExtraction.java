package com.patientcase.document;

import com.patientcase.kiosk.KioskIntake;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Metadata about automated digitization/intelligence processing of an
 * uploaded medical document (prescription, lab report, discharge summary).
 *
 * The extraction result is stored as structured JSON so the physician review
 * can present chronologically organized, source-traced findings. Statuses
 * clearly distinguish processed from unavailable results — extraction is
 * never presented as a definitive clinical reading.
 */
@Entity
@Table(name = "document_extractions",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_document_extraction_document",
           columnNames = "document_id"))
public class DocumentExtraction {

    public enum Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        UNSUPPORTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intake_id")
    private KioskIntake intake;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.PENDING;

    /** OCR/digitization provider id (e.g. "tesseract", "mock", "unavailable"). */
    @Column(length = 50)
    private String provider;

    /** Structured extraction payload (medications, investigations, values, dates). */
    @Column(name = "extracted_json", columnDefinition = "TEXT")
    private String extractedJson;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public DocumentExtraction() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    public KioskIntake getIntake() { return intake; }
    public void setIntake(KioskIntake intake) { this.intake = intake; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getExtractedJson() { return extractedJson; }
    public void setExtractedJson(String extractedJson) { this.extractedJson = extractedJson; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}