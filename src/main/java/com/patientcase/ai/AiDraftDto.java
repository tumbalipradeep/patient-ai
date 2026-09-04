package com.patientcase.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed DTO representing the structured clinical draft produced by the AI conversation.
 *
 * ALLOWED fields (patient-reported only):
 *   - chiefComplaint
 *   - historyOfPresentIllness
 *   - relevantHistory
 *   - symptoms (list of AiSymptomDraft)
 *
 * PROHIBITED fields — must always be null/empty and are never applied to the record:
 *   - diagnoses      (requires clinical judgment)
 *   - treatments     (requires clinical authority)
 *   - examinations   (requires physical examination)
 *   - vitals         (must be measured)
 *   - assessmentNotes / clinicalImpression (clinician's own synthesis)
 *   - followUpDate / followUpInstructions  (clinician decision)
 *
 * The AiDraftValidator enforces these rules server-side before persistence.
 * We also deserialise any prohibited fields the AI may have included so we
 * can explicitly detect and reject them — we never silently transform their meaning.
 */
public class AiDraftDto {

    // ---- Allowed: patient-reported narrative ----
    private String chiefComplaint;           // max 2000
    private String historyOfPresentIllness;  // max 5000
    private String relevantHistory;          // max 5000
    private List<AiSymptomDraft> symptoms = new ArrayList<>();

    /**
     * Patient-safety observations based solely on patient-reported information.
     * NOT diagnoses. Require clinician assessment. Max 10 items, max 500 chars each.
     */
    private List<String> redFlags = new ArrayList<>();

    // ---- Prohibited: captured only to detect violations, never applied ----
    // Jackson deserialises these so the validator can reject any non-empty values.
    private List<Object> diagnoses    = new ArrayList<>();
    private List<Object> treatments   = new ArrayList<>();
    private List<Object> examinations = new ArrayList<>();

    // These two are also prohibited from being applied; captured for rejection.
    private String assessmentNotes;    // prohibited — do NOT convert to historyOfPresentIllness
    private String clinicalImpression; // prohibited

    public AiDraftDto() {}

    // ---- Allowed fields ----

    public String getChiefComplaint() { return chiefComplaint; }
    public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }

    public String getHistoryOfPresentIllness() { return historyOfPresentIllness; }
    public void setHistoryOfPresentIllness(String historyOfPresentIllness) {
        this.historyOfPresentIllness = historyOfPresentIllness;
    }

    public String getRelevantHistory() { return relevantHistory; }
    public void setRelevantHistory(String relevantHistory) { this.relevantHistory = relevantHistory; }

    public List<AiSymptomDraft> getSymptoms() { return symptoms; }
    public void setSymptoms(List<AiSymptomDraft> symptoms) {
        this.symptoms = symptoms != null ? symptoms : new ArrayList<>();
    }

    public List<String> getRedFlags() { return redFlags; }
    public void setRedFlags(List<String> redFlags) {
        this.redFlags = redFlags != null ? redFlags : new ArrayList<>();
    }

    // ---- Prohibited fields (deserialised for violation detection only) ----

    public List<Object> getDiagnoses() { return diagnoses; }
    public void setDiagnoses(List<Object> diagnoses) {
        this.diagnoses = diagnoses != null ? diagnoses : new ArrayList<>();
    }

    public List<Object> getTreatments() { return treatments; }
    public void setTreatments(List<Object> treatments) {
        this.treatments = treatments != null ? treatments : new ArrayList<>();
    }

    public List<Object> getExaminations() { return examinations; }
    public void setExaminations(List<Object> examinations) {
        this.examinations = examinations != null ? examinations : new ArrayList<>();
    }

    public String getAssessmentNotes() { return assessmentNotes; }
    public void setAssessmentNotes(String assessmentNotes) { this.assessmentNotes = assessmentNotes; }

    public String getClinicalImpression() { return clinicalImpression; }
    public void setClinicalImpression(String clinicalImpression) {
        this.clinicalImpression = clinicalImpression;
    }
}
