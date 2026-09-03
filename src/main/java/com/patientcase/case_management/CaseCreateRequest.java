package com.patientcase.case_management;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CaseCreateRequest {

    @NotNull(message = "Patient is required")
    private Long patientId;

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    private String title;

    @NotBlank(message = "Chief complaint is required")
    @Size(min = 5, max = 2000, message = "Chief complaint must be between 5 and 2000 characters")
    private String chiefComplaint;

    @NotNull(message = "Status is required")
    private CaseStatus status = CaseStatus.OPEN;

    @NotNull(message = "Priority is required")
    private CasePriority priority = CasePriority.MEDIUM;

    // Getters and Setters
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getChiefComplaint() { return chiefComplaint; }
    public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }

    public CaseStatus getStatus() { return status; }
    public void setStatus(CaseStatus status) { this.status = status; }

    public CasePriority getPriority() { return priority; }
    public void setPriority(CasePriority priority) { this.priority = priority; }
}
