package com.patientcase.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Focused unit tests for Batch 4 AI Clinical Intake Intelligence.
 *
 * Tests cover:
 * - Adaptive questioning (one question per turn, not repeated)
 * - Structured per-turn JSON parsing
 * - Fact / inference / unknown separation
 * - Red-flag detection and validation
 * - Prohibited clinical claims rejected
 * - Malformed AI responses handled safely
 *
 * All tests use AiService.testableParseReply() — no Spring context, no network.
 */
class AiBatch4IntelligenceTest {

    private AiService service;
    private AiDraftValidator validator;

    @BeforeEach
    void setUp() {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        org.springframework.boot.web.client.RestTemplateBuilder builder =
                new org.springframework.boot.web.client.RestTemplateBuilder();
        service = new AiService(builder, mapper);
        setField(service, "enabled", false);
        setField(service, "apiKey", "");
        setField(service, "model", "gpt-4o-mini");
        setField(service, "apiUrl", "http://localhost/fake");
        validator = new AiDraftValidator();
    }

    // =========================================================================
    // Adaptive questioning — one question per turn
    // =========================================================================

    @Test
    void parseReply_conversationalTurn_returnsSingleQuestion() {
        String json = """
            {
              "complete": false,
              "nextQuestion": "How long have you had this headache?",
              "patientReportedFacts": ["Patient reports headache"],
              "inferredInformation": [],
              "missingInformation": ["duration", "severity"],
              "redFlags": [],
              "urgentFlag": false
            }
            """;
        AiChatResponse response = service.testableParseReply(json);

        assertThat(response.isComplete()).isFalse();
        assertThat(response.getReply()).isEqualTo("How long have you had this headache?");
        assertThat(response.getError()).isNull();
        assertThat(response.isUrgentFlag()).isFalse();
        assertThat(response.getRedFlags()).isNull();  // no red flags → field absent
    }

    @Test
    void parseReply_conversationalTurn_replyIsExactlyNextQuestion() {
        // Guarantees one-question-at-a-time: the reply shown to the patient is
        // exactly and only the nextQuestion field, never more.
        String json = """
            {
              "complete": false,
              "nextQuestion": "Where exactly is the pain located?",
              "patientReportedFacts": ["Headache for 3 days"],
              "inferredInformation": [],
              "missingInformation": ["location", "character"],
              "redFlags": [],
              "urgentFlag": false
            }
            """;
        AiChatResponse response = service.testableParseReply(json);

        assertThat(response.getReply()).isEqualTo("Where exactly is the pain located?");
        // Must be a single question, not multiple sentences that would imply multiple questions
        long questionMarks = response.getReply().chars().filter(c -> c == '?').count();
        assertThat(questionMarks).isLessThanOrEqualTo(1);
    }

    @Test
    void parseReply_missingNextQuestion_returnsError() {
        // If AI returns complete=false but omits nextQuestion, that is a malformed response
        String json = """
            {
              "complete": false,
              "patientReportedFacts": [],
              "redFlags": [],
              "urgentFlag": false
            }
            """;
        AiChatResponse response = service.testableParseReply(json);

        // nextQuestion is required for non-complete turns
        assertThat(response.getError()).isNotBlank();
        assertThat(response.isComplete()).isFalse();
    }

    // =========================================================================
    // Already-known information not unnecessarily requested again
    // =========================================================================

    @Test
    void parseReply_patientReportedFacts_arePopulated() {
        // When the AI returns patientReportedFacts, those are the known items.
        // The missingInformation list should NOT overlap with patientReportedFacts.
        String json = """
            {
              "complete": false,
              "nextQuestion": "Is the pain sharp or dull?",
              "patientReportedFacts": ["Headache for 3 days", "Severity 7/10"],
              "inferredInformation": [],
              "missingInformation": ["character", "location"],
              "redFlags": [],
              "urgentFlag": false
            }
            """;
        AiChatResponse response = service.testableParseReply(json);

        assertThat(response.isComplete()).isFalse();
        // The question should not ask about facts already reported
        assertThat(response.getReply()).doesNotContain("3 days");
        assertThat(response.getReply()).doesNotContain("7/10");
    }

    // =========================================================================
    // Conversational UX enrichment (chips, section, progress, facts/inferred)
    // =========================================================================

    @Test
    void parseReply_conversationalTurn_populatesChatUxFields() {
        String json = """
            {
              "complete": false,
              "nextQuestion": "How long have you had the cough?",
              "patientReportedFacts": ["Cough for 2 weeks"],
              "inferredInformation": [],
              "missingInformation": ["duration", "severity"],
              "section": "HPI",
              "sectionProgress": 40,
              "suggestedAnswers": ["1 day", "2-3 days", "More than a week", "Not sure"],
              "allowsOtherText": true,
              "redFlags": [],
              "urgentFlag": false
            }
            """;
        AiChatResponse response = service.testableParseReply(json);

        assertThat(response.isComplete()).isFalse();
        assertThat(response.getReply()).isEqualTo("How long have you had the cough?");
        assertThat(response.getPatientReportedFacts()).containsExactly("Cough for 2 weeks");
        assertThat(response.getSuggestedAnswers())
                .containsExactly("1 day", "2-3 days", "More than a week", "Not sure");
        assertThat(response.getSection()).isEqualTo("HPI");
        assertThat(response.getSectionProgress()).isEqualTo(40);
        assertThat(response.getAllowOtherText()).isTrue();
        assertThat(response.getInferredInformation()).isNull();
    }

    @Test
    void parseReply_unknownSection_isNormalisedToOther() {
        String json = """
            {
              "complete": false,
              "nextQuestion": "Any family history of this?",
              "patientReportedFacts": [],
              "inferredInformation": [],
              "missingInformation": ["family history"],
              "section": "MYTHICAL_SECTION",
              "sectionProgress": 55,
              "suggestedAnswers": [],
              "redFlags": [],
              "urgentFlag": false
            }
            """;
        AiChatResponse response = service.testableParseReply(json);

        // Unknown tokens never reach the browser — the UI only ever sees a known value
        assertThat(response.getSection()).isEqualTo("OTHER");
    }

    @Test
    void parseReply_suggestedAnswers_areBoundedAndTruncated() {
        String longAnswer = "a".repeat(200);
        String json = """
            {
              "complete": false,
              "nextQuestion": "How severe?",
              "patientReportedFacts": [],
              "inferredInformation": [],
              "missingInformation": ["severity"],
              "suggestedAnswers": ["0", "1", "2", "3", "4", "PLEASE_DROP_ME", "%s"],
              "allowsOtherText": false,
              "redFlags": [],
              "urgentFlag": false
            }
            """.formatted(longAnswer);
        AiChatResponse response = service.testableParseReply(json);

        // At most 4 chips, each truncated to 60 chars; blanks dropped
        assertThat(response.getSuggestedAnswers()).hasSize(4);
        assertThat(response.getSuggestedAnswers())
                .doesNotContain("PLEASE_DROP_ME");
        assertThat(response.getSuggestedAnswers()).allSatisfy(a ->
                assertThat(a.length()).isLessThanOrEqualTo(60));
        assertThat(response.getAllowOtherText()).isFalse();
    }

    @Test
    void parseReply_completeTurn_finalProgressHundredAllowOtherFalse() {
        String json = """
            {
              "complete": true,
              "nextQuestion": null,
              "patientReportedFacts": ["Cough for 2 weeks"],
              "inferredInformation": [],
              "missingInformation": [],
              "section": "HPI",
              "sectionProgress": 100,
              "suggestedAnswers": [],
              "allowsOtherText": false,
              "redFlags": [],
              "urgentFlag": false,
              "completionMessage": "Summary ready.",
              "draft": {
                "chiefComplaint": "Cough",
                "symptoms": []
              }
            }
            """;
        AiChatResponse response = service.testableParseReply(json);

        assertThat(response.isComplete()).isTrue();
        assertThat(response.getReply()).isEqualTo("Summary ready.");
        assertThat(response.getSectionProgress()).isEqualTo(100);
        assertThat(response.getAllowOtherText()).isFalse();
        assertThat(response.getStructuredData()).contains("Cough");
    }

    @Test
    void parseReply_progressOutOfRange_isClamped() {
        String json = """
            {
              "complete": false,
              "nextQuestion": "Question?",
              "patientReportedFacts": [],
              "inferredInformation": [],
              "missingInformation": ["x"],
              "sectionProgress": 350,
              "redFlags": [],
              "urgentFlag": false
            }
            """;
        AiChatResponse response = service.testableParseReply(json);

        assertThat(response.getSectionProgress()).isEqualTo(100);
    }

    // =========================================================================
    // Fact / inference / unknown separation
    // =========================================================================

    @Test
    void validator_symptomWithPatientReportedConfidence_preserved() {
        AiDraftDto raw = new AiDraftDto();
        AiSymptomDraft s = new AiSymptomDraft();
        s.setName("Headache");
        s.setSeverity("MODERATE");
        s.setOnset("GRADUAL");
        s.setConfidence(DraftFieldConfidence.PATIENT_REPORTED);
        raw.setSymptoms(List.of(s));

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getSymptoms().get(0).getConfidence())
                .isEqualTo(DraftFieldConfidence.PATIENT_REPORTED);
    }

    @Test
    void validator_symptomWithAiInferredConfidence_preserved() {
        AiDraftDto raw = new AiDraftDto();
        AiSymptomDraft s = new AiSymptomDraft();
        s.setName("Photophobia");
        s.setSeverity(null);
        s.setOnset("UNKNOWN");
        s.setConfidence(DraftFieldConfidence.AI_INFERRED);
        raw.setSymptoms(List.of(s));

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getSymptoms().get(0).getConfidence())
                .isEqualTo(DraftFieldConfidence.AI_INFERRED);
        // AI_INFERRED severity null is preserved — not defaulted to MILD
        assertThat(clean.getSymptoms().get(0).getSeverity()).isNull();
    }

    @Test
    void validator_aiInferredConfidence_neverBecomesPatientReported() {
        // Core safety rule: AI inferences must not be relabelled as patient-reported facts
        AiDraftDto raw = new AiDraftDto();
        AiSymptomDraft s = new AiSymptomDraft();
        s.setName("Nausea");
        s.setConfidence(DraftFieldConfidence.AI_INFERRED);
        raw.setSymptoms(List.of(s));

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getSymptoms().get(0).getConfidence())
                .isEqualTo(DraftFieldConfidence.AI_INFERRED);
        assertThat(clean.getSymptoms().get(0).getConfidence())
                .isNotEqualTo(DraftFieldConfidence.PATIENT_REPORTED);
    }

    // =========================================================================
    // Red-flag detection
    // =========================================================================

    @Test
    void parseReply_withRedFlags_surfacedOnResponse() {
        String json = """
            {
              "complete": false,
              "nextQuestion": "Are you able to move all your limbs normally?",
              "patientReportedFacts": ["Sudden severe headache described as worst of life"],
              "inferredInformation": [],
              "missingInformation": ["neurological symptoms"],
              "redFlags": ["Patient reports sudden onset of most severe headache ever experienced."],
              "urgentFlag": true
            }
            """;
        AiChatResponse response = service.testableParseReply(json);

        assertThat(response.getRedFlags()).isNotNull().hasSize(1);
        assertThat(response.getRedFlags().get(0)).contains("severe headache");
        assertThat(response.isUrgentFlag()).isTrue();
    }

    @Test
    void parseReply_withRedFlags_completionStillWorks() {
        String json = """
            {
              "complete": true,
              "nextQuestion": null,
              "patientReportedFacts": ["Chest pain radiating to left arm"],
              "inferredInformation": [],
              "missingInformation": [],
              "redFlags": ["Patient reports chest pain radiating to left arm with sweating."],
              "urgentFlag": true,
              "completionMessage": "I have gathered the information. Please seek medical attention immediately.",
              "draft": {
                "chiefComplaint": "Chest pain",
                "historyOfPresentIllness": "Sudden chest pain with left arm radiation and sweating.",
                "relevantHistory": "",
                "symptoms": [
                  {"name": "Chest pain", "severity": "SEVERE", "onset": "SUDDEN",
                   "notes": "Radiates to left arm", "confidence": "PATIENT_REPORTED"}
                ]
              }
            }
            """;
        AiChatResponse response = service.testableParseReply(json);

        assertThat(response.isComplete()).isTrue();
        assertThat(response.getRedFlags()).hasSize(1);
        assertThat(response.isUrgentFlag()).isTrue();
        assertThat(response.getStructuredData()).contains("chiefComplaint");
    }

    @Test
    void validator_redFlagsAreSafeObservations_accepted() {
        List<String> flags = List.of(
                "Patient reports sudden onset of worst headache of their life.",
                "Patient reports chest pain with left arm radiation and sweating."
        );
        List<String> clean = validator.validateRedFlags(flags);
        assertThat(clean).hasSize(2);
    }

    @Test
    void validator_redFlagWithDiagnosisLanguage_throwsValidationException() {
        List<String> flags = List.of("Possible subarachnoid haemorrhage — needs CT scan and diagnoses.");
        assertThatThrownBy(() -> validator.validateRedFlags(flags))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class)
                .hasMessageContaining("prohibited clinical language");
    }

    @Test
    void validator_redFlagWithPrescribingLanguage_throwsValidationException() {
        List<String> flags = List.of("Patient should be prescribed aspirin immediately.");
        assertThatThrownBy(() -> validator.validateRedFlags(flags))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class);
    }

    @Test
    void validator_redFlagsNeverBecomeDiagnoses() {
        // Even valid red flags must not appear as diagnoses in the final draft
        AiDraftDto raw = new AiDraftDto();
        raw.setChiefComplaint("Chest pain");
        raw.setRedFlags(List.of("Patient reports chest pain with sweating."));
        // diagnoses list must still be empty
        assertThat(raw.getDiagnoses()).isEmpty();

        AiDraftDto clean = validator.validate(raw);
        assertThat(clean.getDiagnoses()).isEmpty();
        // Red flags are preserved as-is
        assertThat(clean.getRedFlags()).hasSize(1);
    }

    @Test
    void validator_tooManyRedFlags_cappedAt10() {
        List<String> flags = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            flags.add("Patient reports symptom number " + i + " which is concerning.");
        }
        List<String> clean = validator.validateRedFlags(flags);
        assertThat(clean).hasSize(10);
    }

    @Test
    void validator_longRedFlag_truncated() {
        String longFlag = "x".repeat(600);
        List<String> clean = validator.validateRedFlags(List.of(longFlag));
        assertThat(clean.get(0)).hasSize(500);
    }

    @Test
    void validator_nullOrBlankRedFlags_dropped() {
        List<String> flags = java.util.Arrays.asList(null, "   ", "Valid patient-reported observation.");
        List<String> clean = validator.validateRedFlags(flags);
        assertThat(clean).hasSize(1);
        assertThat(clean.get(0)).isEqualTo("Valid patient-reported observation.");
    }

    // =========================================================================
    // Prohibited clinical claims rejected
    // =========================================================================

    @Test
    void validator_assessmentNotesInDraft_throwsValidationException() {
        AiDraftDto raw = new AiDraftDto();
        raw.setAssessmentNotes("Likely migraine.");
        assertThatThrownBy(() -> validator.validate(raw))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class)
                .hasMessageContaining("assessmentNotes");
    }

    @Test
    void validator_clinicalImpressionInDraft_throwsValidationException() {
        AiDraftDto raw = new AiDraftDto();
        raw.setClinicalImpression("Tension headache.");
        assertThatThrownBy(() -> validator.validate(raw))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class)
                .hasMessageContaining("clinicalImpression");
    }

    @Test
    void validator_diagnosisInDraft_throwsValidationException() {
        AiDraftDto raw = new AiDraftDto();
        raw.setDiagnoses(List.of("Migraine"));
        assertThatThrownBy(() -> validator.validate(raw))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class)
                .hasMessageContaining("diagnoses");
    }

    @Test
    void validator_treatmentInDraft_throwsValidationException() {
        AiDraftDto raw = new AiDraftDto();
        raw.setTreatments(List.of("Ibuprofen 400mg"));
        assertThatThrownBy(() -> validator.validate(raw))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class)
                .hasMessageContaining("treatments");
    }

    @Test
    void validator_examinationInDraft_throwsValidationException() {
        AiDraftDto raw = new AiDraftDto();
        raw.setExaminations(List.of("Neuro exam: pupils equal and reactive"));
        assertThatThrownBy(() -> validator.validate(raw))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class)
                .hasMessageContaining("examinations");
    }

    // =========================================================================
    // Malformed AI responses handled safely
    // =========================================================================

    @Test
    void parseReply_freeTextResponse_fallsBackToPlainReply() {
        // Old-style free-text response — AI ignored the JSON format instruction.
        // Must not crash; graceful fallback to plain reply.
        String freeText = "What is your main symptom?";
        AiChatResponse response = service.testableParseReply(freeText);

        // Falls back to a plain reply (no structured data, no error)
        assertThat(response.getError()).isNull();
        assertThat(response.isDisabled()).isFalse();
        assertThat(response.getReply()).isNotBlank();
    }

    @Test
    void parseReply_invalidJson_returnsPlainReply() {
        String invalid = "{ this is not valid json at all";
        AiChatResponse response = service.testableParseReply(invalid);
        // Graceful fallback — not a crash, not an empty response
        assertThat(response.getError()).isNull();
        assertThat(response.getReply()).isNotBlank();
    }

    @Test
    void parseReply_emptyString_returnsError() {
        AiChatResponse response = service.testableParseReply("");
        assertThat(response.getError()).isNotBlank();
    }

    @Test
    void parseReply_nullString_returnsError() {
        AiChatResponse response = service.testableParseReply(null);
        assertThat(response.getError()).isNotBlank();
    }

    @Test
    void parseReply_jsonFenceWrapped_parsedCorrectly() {
        // Model wrapped its JSON in markdown code fences despite instructions.
        // Must be unwrapped and parsed.
        String wrapped = "```json\n" +
                "{\n" +
                "  \"complete\": false,\n" +
                "  \"nextQuestion\": \"How long have you had this?\",\n" +
                "  \"patientReportedFacts\": [],\n" +
                "  \"inferredInformation\": [],\n" +
                "  \"missingInformation\": [\"duration\"],\n" +
                "  \"redFlags\": [],\n" +
                "  \"urgentFlag\": false\n" +
                "}\n```";

        AiChatResponse response = service.testableParseReply(wrapped);
        assertThat(response.isComplete()).isFalse();
        assertThat(response.getReply()).isEqualTo("How long have you had this?");
    }

    @Test
    void parseReply_completeTurnWithDraft_returnsDraftReady() {
        String json = """
            {
              "complete": true,
              "nextQuestion": null,
              "patientReportedFacts": ["Headache for 3 days", "Severity 6/10"],
              "inferredInformation": [],
              "missingInformation": [],
              "redFlags": [],
              "urgentFlag": false,
              "completionMessage": "Thank you. I have enough information for a preliminary draft.",
              "draft": {
                "chiefComplaint": "Headache",
                "historyOfPresentIllness": "Gradual onset headache for 3 days, severity 6/10.",
                "relevantHistory": "No known allergies.",
                "symptoms": [
                  {
                    "name": "Headache",
                    "duration": "3 days",
                    "severity": "MODERATE",
                    "onset": "GRADUAL",
                    "notes": "Frontal, throbbing",
                    "confidence": "PATIENT_REPORTED"
                  }
                ]
              }
            }
            """;
        AiChatResponse response = service.testableParseReply(json);

        assertThat(response.isComplete()).isTrue();
        assertThat(response.getReply()).contains("preliminary draft");
        assertThat(response.getStructuredData()).isNotBlank();
        assertThat(response.getStructuredData()).contains("Headache");
        assertThat(response.getStructuredData()).contains("chiefComplaint");
    }

    @Test
    void parseReply_completeTurnWithDraft_canBeValidated() {
        // Prove the full path: AI response → parseReply → structuredData → AiDraftValidator
        String json = """
            {
              "complete": true,
              "nextQuestion": null,
              "patientReportedFacts": [],
              "inferredInformation": [],
              "missingInformation": [],
              "redFlags": [],
              "urgentFlag": false,
              "completionMessage": "Draft ready.",
              "draft": {
                "chiefComplaint": "Back pain",
                "historyOfPresentIllness": "Lower back pain for 1 week.",
                "relevantHistory": "",
                "symptoms": [
                  {"name": "Back pain", "severity": "MILD", "onset": "GRADUAL",
                   "confidence": "PATIENT_REPORTED"}
                ]
              }
            }
            """;
        AiChatResponse response = service.testableParseReply(json);
        assertThat(response.isComplete()).isTrue();

        // Validate the structured data through AiDraftValidator
        AiDraftDto draft = validator.parseAndValidate(response.getStructuredData());
        assertThat(draft.getChiefComplaint()).isEqualTo("Back pain");
        assertThat(draft.getSymptoms()).hasSize(1);
        assertThat(draft.getSymptoms().get(0).getConfidence())
                .isEqualTo(DraftFieldConfidence.PATIENT_REPORTED);
        // Safety: prohibited fields must be empty
        assertThat(draft.getDiagnoses()).isEmpty();
        assertThat(draft.getTreatments()).isEmpty();
        assertThat(draft.getExaminations()).isEmpty();
    }

    @Test
    void parseReply_completeTurnWithProhibitedDraftField_strippedBeforeValidation() {
        // When the AI embeds assessmentNotes in the draft, the DraftPayload whitelist
        // (via @JsonIgnoreProperties) strips it before serialisation — so it never
        // reaches the validator at all. The draft is still accepted (just without
        // the prohibited field). This is the correct whitelisting behaviour.
        String json = """
            {
              "complete": true,
              "nextQuestion": null,
              "patientReportedFacts": [],
              "inferredInformation": [],
              "missingInformation": [],
              "redFlags": [],
              "urgentFlag": false,
              "completionMessage": "Draft ready.",
              "draft": {
                "chiefComplaint": "Fever",
                "assessmentNotes": "Likely viral infection",
                "symptoms": []
              }
            }
            """;
        AiChatResponse response = service.testableParseReply(json);
        assertThat(response.isComplete()).isTrue();
        assertThat(response.getStructuredData()).isNotBlank();

        // The DraftPayload whitelist strips assessmentNotes — validator accepts the clean draft
        AiDraftDto draft = validator.parseAndValidate(response.getStructuredData());
        assertThat(draft.getChiefComplaint()).isEqualTo("Fever");
        // assessmentNotes was stripped by the whitelist — verify it is absent from the draft
        assertThat(draft.getAssessmentNotes()).isNull();
        assertThat(draft.getDiagnoses()).isEmpty();
    }

    @Test
    void validator_prohibitedFieldsInStoredDraft_stillRejected() {
        // Defence-in-depth: if malformed JSON ever reaches parseAndValidate directly
        // (e.g. a direct API call bypassing AiService), the validator must still reject it.
        String maliciousDraftJson =
            "{\"chiefComplaint\":\"Fever\",\"diagnoses\":[\"Malaria\"],\"symptoms\":[]}";
        assertThatThrownBy(() -> validator.parseAndValidate(maliciousDraftJson))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class)
                .hasMessageContaining("diagnoses");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Could not set field " + fieldName, e);
        }
    }
}
