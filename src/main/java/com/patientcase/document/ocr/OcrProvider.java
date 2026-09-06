package com.patientcase.document.ocr;

import java.util.List;

/**
 * Contract for digitizing/scanned medical document intelligence.
 *
 * Implementations extract structured, physician-attention information from an
 * uploaded medical document. A safe default implementation returns
 * {@link DocumentIntelligence#unsupported()} when no OCR provider is
 * configured — the application never pretends OCR occurred.
 *
 * Extraction output is framed as a physician-attention aid, never a
 * definitive clinical decision, and never invents values that are not
 * clearly present in the source.
 */
public interface OcrProvider {

    /** Provider identifier reported on DocumentExtraction records. */
    String providerId();

    /** True when this provider can actually process documents in this deployment. */
    boolean isAvailable();

    /**
     * Process an uploaded document's stored bytes into structured intelligence.
     * Must never throw — errors are returned as a FAILED result.
     */
    DocumentIntelligence extract(byte[] content, String contentType, String originalFilename);

    /**
     * Structured intelligence extracted from a document.
     * Never fabricates values — fields the provider cannot determine are absent.
     */
    final class DocumentIntelligence {

        private final boolean processed;
        private final boolean uncertain;
        private final String error;
        private final List<MedicationEntry> medications;
        private final List<InvestigationEntry> investigations;
        private final List<String> diagnoses;
        private final List<String> procedures;
        private final List<String> notes;

        private DocumentIntelligence(boolean processed, boolean uncertain, String error,
                                     List<MedicationEntry> medications,
                                     List<InvestigationEntry> investigations,
                                     List<String> diagnoses, List<String> procedures,
                                     List<String> notes) {
            this.processed = processed;
            this.uncertain = uncertain;
            this.error = error;
            this.medications = medications;
            this.investigations = investigations;
            this.diagnoses = diagnoses;
            this.procedures = procedures;
            this.notes = notes;
        }

        public static DocumentIntelligence unsupported() {
            return new DocumentIntelligence(false, true,
                    "No document digitization provider is available for this document type.",
                    List.of(), List.of(), List.of(), List.of(), List.of());
        }

        public static DocumentIntelligence failed(String error) {
            return new DocumentIntelligence(false, true, error,
                    List.of(), List.of(), List.of(), List.of(), List.of());
        }

        public static DocumentIntelligence completed(List<MedicationEntry> medications,
                                                     List<InvestigationEntry> investigations,
                                                     List<String> diagnoses,
                                                     List<String> procedures,
                                                     List<String> notes) {
            return new DocumentIntelligence(true, false, null,
                    medications, investigations, diagnoses, procedures, notes);
        }

        public boolean isProcessed() { return processed; }
        public boolean isUncertain() { return uncertain; }
        public String getError() { return error; }
        public List<MedicationEntry> getMedications() { return medications; }
        public List<InvestigationEntry> getInvestigations() { return investigations; }
        public List<String> getDiagnoses() { return diagnoses; }
        public List<String> getProcedures() { return procedures; }
        public List<String> getNotes() { return notes; }
    }

    /** A medication/dosage entry clearly present in the source document. */
    final class MedicationEntry {
        private final String name;
        private final String dosage;
        private final String frequency;
        private final String duration;

        public MedicationEntry(String name, String dosage, String frequency, String duration) {
            this.name = name;
            this.dosage = dosage;
            this.frequency = frequency;
            this.duration = duration;
        }

        public String getName() { return name; }
        public String getDosage() { return dosage; }
        public String getFrequency() { return frequency; }
        public String getDuration() { return duration; }
    }

    /** An investigation/lab entry with optional value and reference range. */
    final class InvestigationEntry {
        private final String investigation;
        private final String value;
        private final String referenceRange;
        private final boolean abnormal;
        private final String recordedOn;

        public InvestigationEntry(String investigation, String value, String referenceRange,
                                  boolean abnormal, String recordedOn) {
            this.investigation = investigation;
            this.value = value;
            this.referenceRange = referenceRange;
            this.abnormal = abnormal;
            this.recordedOn = recordedOn;
        }

        public String getInvestigation() { return investigation; }
        public String getValue() { return value; }
        public String getReferenceRange() { return referenceRange; }
        public boolean isAbnormal() { return abnormal; }
        public String getRecordedOn() { return recordedOn; }
    }
}