package com.patientcase.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for AiDraftValidator.
 * No Spring context — fast, pure Java.
 */
class AiDraftValidatorTest {

    private AiDraftValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AiDraftValidator();
    }

    // ---- Valid draft ----

    @Test
    void validate_validDraft_returnsCleanDraft() {
        AiDraftDto raw = new AiDraftDto();
        raw.setChiefComplaint("Headache");
        raw.setHistoryOfPresentIllness("Started 3 days ago, throbbing.");
        raw.setRelevantHistory("No known allergies.");

        AiSymptomDraft s = new AiSymptomDraft();
        s.setName("Headache");
        s.setDuration("3 days");
        s.setSeverity("MODERATE");
        s.setOnset("GRADUAL");
        s.setNotes("Gets worse in the morning");
        s.setConfidence(DraftFieldConfidence.PATIENT_REPORTED);
        raw.setSymptoms(List.of(s));

        AiDraftDto clean = validator.validate(raw);

        assertThat(clean.getChiefComplaint()).isEqualTo("Headache");
        assertThat(clean.getSymptoms()).hasSize(1);
        assertThat(clean.getSymptoms().get(0).getSeverity()).isEqualTo("MODERATE");
        assertThat(clean.getSymptoms().get(0).getOnset()).isEqualTo("GRADUAL");
        assertThat(clean.getDiagnoses()).isEmpty();
        assertThat(clean.getTreatments()).isEmpty();
        assertThat(clean.getExaminations()).isEmpty();
    }

    // ---- Hard rule: prohibited diagnoses ----

    @Test
    void validate_diagnosesPresent_throwsValidationException() {
        AiDraftDto raw = new AiDraftDto();
        raw.setChiefComplaint("Fever");
        raw.setDiagnoses(List.of("Malaria"));

        assertThatThrownBy(() -> validator.validate(raw))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class)
                .hasMessageContaining("diagnoses");
    }

    // ---- Hard rule: prohibited treatments ----

    @Test
    void validate_treatmentsPresent_throwsValidationException() {
        AiDraftDto raw = new AiDraftDto();
        raw.setChiefComplaint("Fever");
        raw.setTreatments(List.of("Paracetamol"));

        assertThatThrownBy(() -> validator.validate(raw))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class)
                .hasMessageContaining("treatments");
    }

    // ---- Hard rule: prohibited examinations ----

    @Test
    void validate_examinationsPresent_throwsValidationException() {
        AiDraftDto raw = new AiDraftDto();
        raw.setChiefComplaint("Fever");
        raw.setExaminations(List.of("Abdomen: tender"));

        assertThatThrownBy(() -> validator.validate(raw))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class)
                .hasMessageContaining("examinations");
    }

    // ---- Hard rule: prohibited assessmentNotes ----

    @Test
    void validate_assessmentNotesPresent_throwsValidationException() {
        AiDraftDto raw = new AiDraftDto();
        raw.setChiefComplaint("Cough");
        raw.setAssessmentNotes("Likely viral infection");

        assertThatThrownBy(() -> validator.validate(raw))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class)
                .hasMessageContaining("assessmentNotes");
    }

    // ---- Hard rule: prohibited clinicalImpression ----

    @Test
    void validate_clinicalImpressionPresent_throwsValidationException() {
        AiDraftDto raw = new AiDraftDto();
        raw.setChiefComplaint("Cough");
        raw.setClinicalImpression("Upper respiratory tract infection");

        assertThatThrownBy(() -> validator.validate(raw))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class)
                .hasMessageContaining("clinicalImpression");
    }

    // ---- Soft rule: unknown severity → null (not defaulted to MILD) ----

    @Test
    void validate_unknownSeverity_mappedToNull() {
        AiDraftDto raw = new AiDraftDto();
        AiSymptomDraft s = buildSymptom("Nausea");
        s.setSeverity("EXTREME");   // invalid — not in {MILD,MODERATE,SEVERE}
        raw.setSymptoms(List.of(s));

        AiDraftDto clean = validator.validate(raw);

        assertThat(clean.getSymptoms()).hasSize(1);
        assertThat(clean.getSymptoms().get(0).getSeverity()).isNull();
    }

    // ---- Soft rule: null severity → null ----

    @Test
    void validate_nullSeverity_remainsNull() {
        AiDraftDto raw = new AiDraftDto();
        AiSymptomDraft s = buildSymptom("Nausea");
        s.setSeverity(null);
        raw.setSymptoms(List.of(s));

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getSymptoms().get(0).getSeverity()).isNull();
    }

    // ---- Soft rule: unknown onset → UNKNOWN ----

    @Test
    void validate_unknownOnset_defaultsToUnknown() {
        AiDraftDto raw = new AiDraftDto();
        AiSymptomDraft s = buildSymptom("Pain");
        s.setOnset("SLOWLY");   // invalid — not in {SUDDEN,GRADUAL,UNKNOWN}
        raw.setSymptoms(List.of(s));

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getSymptoms().get(0).getOnset()).isEqualTo("UNKNOWN");
    }

    // ---- Soft rule: null onset → UNKNOWN ----

    @Test
    void validate_nullOnset_defaultsToUnknown() {
        AiDraftDto raw = new AiDraftDto();
        AiSymptomDraft s = buildSymptom("Pain");
        s.setOnset(null);
        raw.setSymptoms(List.of(s));

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getSymptoms().get(0).getOnset()).isEqualTo("UNKNOWN");
    }

    // ---- Soft rule: null confidence → PATIENT_REPORTED ----

    @Test
    void validate_nullConfidence_defaultsToPatientReported() {
        AiDraftDto raw = new AiDraftDto();
        AiSymptomDraft s = buildSymptom("Fatigue");
        s.setConfidence(null);
        raw.setSymptoms(List.of(s));

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getSymptoms().get(0).getConfidence())
                .isEqualTo(DraftFieldConfidence.PATIENT_REPORTED);
    }

    // ---- Soft rule: blank symptom name → dropped ----

    @Test
    void validate_blankSymptomName_droppedFromList() {
        AiDraftDto raw = new AiDraftDto();
        AiSymptomDraft blank = buildSymptom("   ");
        AiSymptomDraft valid = buildSymptom("Cough");
        raw.setSymptoms(List.of(blank, valid));

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getSymptoms()).hasSize(1);
        assertThat(clean.getSymptoms().get(0).getName()).isEqualTo("Cough");
    }

    // ---- Soft rule: null symptom in list → dropped ----

    @Test
    void validate_nullSymptomInList_droppedSafely() {
        AiDraftDto raw = new AiDraftDto();
        raw.setSymptoms(java.util.Arrays.asList(null, buildSymptom("Cough")));

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getSymptoms()).hasSize(1);
    }

    // ---- Length validation: text truncated to column limits ----

    @Test
    void validate_chiefComplaintExceedsMaxLength_truncated() {
        String longText = "x".repeat(3000);
        AiDraftDto raw = new AiDraftDto();
        raw.setChiefComplaint(longText);

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getChiefComplaint()).hasSize(2000);
    }

    @Test
    void validate_symptomNameExceedsMaxLength_truncated() {
        AiDraftDto raw = new AiDraftDto();
        AiSymptomDraft s = buildSymptom("a".repeat(500));
        raw.setSymptoms(List.of(s));

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getSymptoms().get(0).getName()).hasSize(255);
    }

    @Test
    void validate_symptomDurationExceedsMaxLength_truncated() {
        AiDraftDto raw = new AiDraftDto();
        AiSymptomDraft s = buildSymptom("Cough");
        s.setDuration("d".repeat(200));
        raw.setSymptoms(List.of(s));

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getSymptoms().get(0).getDuration()).hasSize(100);
    }

    @Test
    void validate_symptomNotesExceedsMaxLength_truncated() {
        AiDraftDto raw = new AiDraftDto();
        AiSymptomDraft s = buildSymptom("Cough");
        s.setNotes("n".repeat(2000));
        raw.setSymptoms(List.of(s));

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getSymptoms().get(0).getNotes()).hasSize(1000);
    }

    // ---- parseAndValidate: valid JSON ----

    @Test
    void parseAndValidate_validJson_returnsDraft() {
        String json = """
            {
              "chiefComplaint": "Chest pain",
              "symptoms": [
                {"name": "Chest pain", "severity": "SEVERE", "onset": "SUDDEN"}
              ]
            }
            """;

        AiDraftDto clean = validator.parseAndValidate(json);
        assertThat(clean.getChiefComplaint()).isEqualTo("Chest pain");
        assertThat(clean.getSymptoms()).hasSize(1);
        assertThat(clean.getSymptoms().get(0).getSeverity()).isEqualTo("SEVERE");
    }

    // ---- parseAndValidate: invalid JSON ----

    @Test
    void parseAndValidate_invalidJson_throwsValidationException() {
        assertThatThrownBy(() -> validator.parseAndValidate("{not valid json"))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class);
    }

    // ---- parseAndValidate: null input ----

    @Test
    void parseAndValidate_nullInput_throwsValidationException() {
        assertThatThrownBy(() -> validator.parseAndValidate(null))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class);
    }

    // ---- validate: null input ----

    @Test
    void validate_nullDraft_throwsValidationException() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class);
    }

    // ---- severity case-insensitive ----

    @Test
    void validate_severityLowerCase_normalised() {
        AiDraftDto raw = new AiDraftDto();
        AiSymptomDraft s = buildSymptom("Pain");
        s.setSeverity("mild");
        raw.setSymptoms(List.of(s));

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getSymptoms().get(0).getSeverity()).isEqualTo("MILD");
    }

    // ---- Helpers ----

    private AiSymptomDraft buildSymptom(String name) {
        AiSymptomDraft s = new AiSymptomDraft();
        s.setName(name);
        s.setSeverity("MILD");
        s.setOnset("UNKNOWN");
        s.setConfidence(DraftFieldConfidence.PATIENT_REPORTED);
        return s;
    }
}
