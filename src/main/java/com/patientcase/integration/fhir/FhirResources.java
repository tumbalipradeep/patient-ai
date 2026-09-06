package com.patientcase.integration.fhir;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FHIR R4 resource mapping DTOs for the ABDM/HIS integration boundary.
 *
 * These are READ-oriented projections used by the export/import adapters.
 * They intentionally cover the subset of FHIR resources relevant to a
 * patient case-taking system: Patient, Encounter, Condition, Observation,
 * MedicationRequest, DiagnosticReport, and DocumentReference.
 *
 * No live ABDM/HIS call is made by default — see {@link com.patientcase.integration.IntegrationService}.
 */
public final class FhirResources {

    private FhirResources() {}

    public static final class Patient {
        public String id;
        public String patientNumber;
        public String firstName;
        public String lastName;
        public LocalDate dateOfBirth;
        public String gender;
        public String phone;
        public String email;
        public String bloodGroup;

        public Patient() {}
        public Patient(String id, String patientNumber, String firstName, String lastName,
                       LocalDate dateOfBirth, String gender, String phone, String email,
                       String bloodGroup) {
            this.id = id;
            this.patientNumber = patientNumber;
            this.firstName = firstName;
            this.lastName = lastName;
            this.dateOfBirth = dateOfBirth;
            this.gender = gender;
            this.phone = phone;
            this.email = email;
            this.bloodGroup = bloodGroup;
        }
    }

    public static final class Encounter {
        public String id;
        public String caseNumber;
        public String status;
        public LocalDateTime date;
        public String clinician;
        public String chiefComplaint;
        public String historyOfPresentIllness;
        public String relevantHistory;

        public Encounter() {}
        public Encounter(String id, String caseNumber, String status, LocalDateTime date,
                         String clinician, String chiefComplaint, String historyOfPresentIllness,
                         String relevantHistory) {
            this.id = id;
            this.caseNumber = caseNumber;
            this.status = status;
            this.date = date;
            this.clinician = clinician;
            this.chiefComplaint = chiefComplaint;
            this.historyOfPresentIllness = historyOfPresentIllness;
            this.relevantHistory = relevantHistory;
        }
    }

    public static final class Condition {
        public String id;
        public String code;
        public String description;
        public String status;
        public LocalDateTime onset;

        public Condition() {}
        public Condition(String id, String code, String description, String status, LocalDateTime onset) {
            this.id = id;
            this.code = code;
            this.description = description;
            this.status = status;
            this.onset = onset;
        }
    }

    public static final class Observation {
        public String id;
        public String category;
        public String code;
        public String value;
        public String unit;
        public String referenceRange;
        public boolean abnormal;
        public LocalDateTime recordedAt;

        public Observation() {}
        public Observation(String id, String category, String code, String value, String unit,
                           String referenceRange, boolean abnormal, LocalDateTime recordedAt) {
            this.id = id;
            this.category = category;
            this.code = code;
            this.value = value;
            this.unit = unit;
            this.referenceRange = referenceRange;
            this.abnormal = abnormal;
            this.recordedAt = recordedAt;
        }
    }

    public static final class Medication {
        public String id;
        public String name;
        public String dosage;
        public String frequency;
        public String duration;
        public String status;

        public Medication() {}
        public Medication(String id, String name, String dosage, String frequency,
                          String duration, String status) {
            this.id = id;
            this.name = name;
            this.dosage = dosage;
            this.frequency = frequency;
            this.duration = duration;
            this.status = status;
        }
    }

    public static final class DocumentReference {
        public String id;
        public String contentType;
        public String originalFilename;
        public LocalDateTime uploadedAt;
        public String description;

        public DocumentReference() {}
        public DocumentReference(String id, String contentType, String originalFilename,
                                 LocalDateTime uploadedAt, String description) {
            this.id = id;
            this.contentType = contentType;
            this.originalFilename = originalFilename;
            this.uploadedAt = uploadedAt;
            this.description = description;
        }
    }

    public static final class Bundle {
        public List<Object> resources;

        public Bundle(List<Object> resources) {
            this.resources = resources;
        }
    }
}