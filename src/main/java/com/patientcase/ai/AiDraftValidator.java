package com.patientcase.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Server-side safety validator for AI-produced structured drafts.
 *
 * Enforces ALL clinical safety rules independently of the LLM system prompt.
 * A prompt injection or model deviation cannot bypass these checks.
 *
 * Hard rules — throw {@link AiDraftValidationException} immediately:
 *   - diagnoses list must be empty
 *   - treatments list must be empty
 *   - examinations list must be empty
 *   - assessmentNotes must be null or blank (prohibited clinical content)
 *   - clinicalImpression must be null or blank (prohibited clinical content)
 *
 * Soft rules — sanitise and continue:
 *   - text fields truncated to column limits
 *   - blank symptom names dropped
 *   - severity mapped to MILD|MODERATE|SEVERE; null if unrecognised (not defaulted)
 *   - onset mapped to SUDDEN|GRADUAL|UNKNOWN (defaults to UNKNOWN if unrecognised)
 *   - confidence mapped to enum; defaults to PATIENT_REPORTED if missing/unrecognised
 *
 * Never logs patient conversation content or raw clinical data.
 */
@Component
public class AiDraftValidator {

    private static final Logger log = LoggerFactory.getLogger(AiDraftValidator.class);

    // Column length limits from CaseTakingForm / DB schema
    private static final int MAX_CHIEF_COMPLAINT    = 2000;
    private static final int MAX_HISTORY            = 5000;
    private static final int MAX_RELEVANT_HISTORY   = 5000;
    private static final int MAX_SYMPTOM_NAME       = 255;
    private static final int MAX_SYMPTOM_DURATION   = 100;
    private static final int MAX_SYMPTOM_NOTES      = 1000;

    private static final Set<String> VALID_SEVERITIES = Set.of("MILD", "MODERATE", "SEVERE");
    private static final Set<String> VALID_ONSETS     = Set.of("SUDDEN", "GRADUAL", "UNKNOWN");

    /**
     * Validate and sanitise a raw {@link AiDraftDto}.
     *
     * @param raw the draft as deserialised from AI output
     * @return a clean, safe draft ready for persistence
     * @throws AiDraftValidationException if any hard safety rule is violated
     */
    public AiDraftDto validate(AiDraftDto raw) {
        if (raw == null) {
            throw new AiDraftValidationException("AI draft is null");
        }

        // ---- HARD RULES ----
        if (!raw.getDiagnoses().isEmpty()) {
            log.warn("AI draft rejected: non-empty diagnoses list (count={})", raw.getDiagnoses().size());
            throw new AiDraftValidationException(
                "AI draft contains diagnoses — this is prohibited. " +
                "Diagnoses require clinical examination and judgment.");
        }
        if (!raw.getTreatments().isEmpty()) {
            log.warn("AI draft rejected: non-empty treatments list (count={})", raw.getTreatments().size());
            throw new AiDraftValidationException(
                "AI draft contains treatments — this is prohibited. " +
                "Treatments require clinical authority.");
        }
        if (!raw.getExaminations().isEmpty()) {
            log.warn("AI draft rejected: non-empty examinations list (count={})", raw.getExaminations().size());
            throw new AiDraftValidationException(
                "AI draft contains clinical examinations — this is prohibited. " +
                "Examinations must be performed by a clinician.");
        }
        if (raw.getAssessmentNotes() != null && !raw.getAssessmentNotes().isBlank()) {
            log.warn("AI draft rejected: assessmentNotes present (prohibited clinical field)");
            throw new AiDraftValidationException(
                "AI draft contains assessmentNotes — this is a prohibited clinical field. " +
                "Clinical assessment requires clinician judgment and must not originate from AI.");
        }
        if (raw.getClinicalImpression() != null && !raw.getClinicalImpression().isBlank()) {
            log.warn("AI draft rejected: clinicalImpression present (prohibited clinical field)");
            throw new AiDraftValidationException(
                "AI draft contains clinicalImpression — this is a prohibited clinical field.");
        }

        // ---- SOFT RULES: build clean draft ----
        AiDraftDto clean = new AiDraftDto();
        clean.setChiefComplaint(truncate(raw.getChiefComplaint(), MAX_CHIEF_COMPLAINT));
        clean.setHistoryOfPresentIllness(truncate(raw.getHistoryOfPresentIllness(), MAX_HISTORY));
        clean.setRelevantHistory(truncate(raw.getRelevantHistory(), MAX_RELEVANT_HISTORY));
        clean.setSymptoms(validateSymptoms(raw.getSymptoms()));

        // Prohibited lists always empty in the clean output
        // (they remain at default empty lists from AiDraftDto constructor)

        return clean;
    }

    private List<AiSymptomDraft> validateSymptoms(List<AiSymptomDraft> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<AiSymptomDraft> clean = new ArrayList<>();
        for (AiSymptomDraft s : raw) {
            if (s == null) continue;
            String name = truncate(s.getName(), MAX_SYMPTOM_NAME);
            if (name == null || name.isBlank()) {
                // Drop symptoms with no name — they carry no information
                continue;
            }
            AiSymptomDraft cs = new AiSymptomDraft();
            cs.setName(name);
            cs.setDuration(truncate(s.getDuration(), MAX_SYMPTOM_DURATION));
            cs.setSeverity(mapSeverity(s.getSeverity()));
            cs.setOnset(mapOnset(s.getOnset()));
            cs.setNotes(truncate(s.getNotes(), MAX_SYMPTOM_NOTES));
            cs.setConfidence(mapConfidence(s.getConfidence()));
            clean.add(cs);
        }
        return clean;
    }

    /**
     * Maps severity string to a valid domain value.
     * Returns null (not MILD) if the value is unrecognised or missing —
     * we must not invent a severity the patient never stated.
     */
    private String mapSeverity(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String upper = raw.strip().toUpperCase();
        return VALID_SEVERITIES.contains(upper) ? upper : null;
    }

    /**
     * Maps onset string to a valid domain value.
     * Defaults to UNKNOWN (not null) — UNKNOWN is the explicit safe default.
     */
    private String mapOnset(String raw) {
        if (raw == null || raw.isBlank()) return "UNKNOWN";
        String upper = raw.strip().toUpperCase();
        return VALID_ONSETS.contains(upper) ? upper : "UNKNOWN";
    }

    /**
     * Maps confidence to enum.
     * Defaults to PATIENT_REPORTED if missing; AI_INFERRED if value is unrecognised.
     */
    private DraftFieldConfidence mapConfidence(DraftFieldConfidence raw) {
        return raw != null ? raw : DraftFieldConfidence.PATIENT_REPORTED;
    }

    /** Truncate a string to maxLen chars. Returns null if input is null. */
    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        String stripped = value.strip();
        if (stripped.isEmpty()) return null;
        return stripped.length() > maxLen ? stripped.substring(0, maxLen) : stripped;
    }

    // ---- Parse helper used by AiIntakeSessionService ----

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Deserialise a JSON string into an {@link AiDraftDto}, then validate it.
     * Throws {@link AiDraftValidationException} on any safety violation or parse failure.
     */
    public AiDraftDto parseAndValidate(String json) {
        if (json == null || json.isBlank()) {
            throw new AiDraftValidationException("AI structured data is empty or null");
        }
        try {
            AiDraftDto raw = objectMapper.readValue(json, AiDraftDto.class);
            return validate(raw);
        } catch (AiDraftValidationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse AI structured data: {}", e.getClass().getSimpleName());
            throw new AiDraftValidationException("AI structured data is not valid JSON: " + e.getMessage());
        }
    }

    /**
     * Serialise a validated draft back to JSON for storage.
     * Package-private — used by AiIntakeSessionService.
     */
    String serialise(AiDraftDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new AiDraftValidationException("Failed to serialise AI draft: " + e.getMessage());
        }
    }

    /**
     * Deserialise stored draft JSON back to {@link AiDraftDto} without re-validation.
     * The stored draft was already validated at save time.
     */
    AiDraftDto deserialise(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, AiDraftDto.class);
        } catch (Exception e) {
            log.warn("Failed to deserialise stored AI draft: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    // ---- Inner exception class ----

    /**
     * Thrown when an AI draft fails a safety rule.
     * Intended to produce a 400 Bad Request via GlobalExceptionHandler.
     */
    public static class AiDraftValidationException extends RuntimeException {
        public AiDraftValidationException(String message) {
            super(message);
        }
    }
}
