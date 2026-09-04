package com.patientcase.ai;

/**
 * A single symptom extracted from the AI conversation.
 *
 * All fields are patient-reported strings from the AI interview.
 * severity and onset are stored as raw strings here and mapped to
 * the domain enums (Severity / Onset) only at apply-time by the
 * validator — this preserves the original AI output for audit purposes.
 *
 * PROHIBITED: no diagnosis, treatment, examination, or vitals data.
 */
public class AiSymptomDraft {

    private String name;       // max 255 chars
    private String duration;   // max 100 chars  e.g. "3 days", "2 weeks"
    private String severity;   // raw — validated to MILD|MODERATE|SEVERE; null if unknown
    private String onset;      // raw — validated to SUDDEN|GRADUAL|UNKNOWN
    private String notes;      // max 1000 chars
    private DraftFieldConfidence confidence = DraftFieldConfidence.PATIENT_REPORTED;

    public AiSymptomDraft() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getOnset() { return onset; }
    public void setOnset(String onset) { this.onset = onset; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public DraftFieldConfidence getConfidence() { return confidence; }
    public void setConfidence(DraftFieldConfidence confidence) { this.confidence = confidence; }
}
