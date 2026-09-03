package com.patientcase.encounter;

import jakarta.validation.constraints.NotNull;

public class EncounterCreateRequest {

    @NotNull(message = "Case is required")
    private Long caseId;

    @NotNull(message = "Clinician is required")
    private Long clinicianId;

    private String notes;

    // Getters and Setters
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }

    public Long getClinicianId() { return clinicianId; }
    public void setClinicianId(Long clinicianId) { this.clinicianId = clinicianId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
