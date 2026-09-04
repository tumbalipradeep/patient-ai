package com.patientcase.ai;

import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;

/**
 * Request body for POST /encounters/{id}/ai-intake/apply.
 *
 * The clinician explicitly names which safe fields they have reviewed and
 * approved from the AI draft. Only fields in the allowed set are accepted;
 * any prohibited field name causes the request to be rejected server-side.
 *
 * Allowed values for approvedFields:
 *   chiefComplaint, historyOfPresentIllness, relevantHistory, symptoms
 *
 * Prohibited values (always rejected):
 *   diagnoses, treatments, examinations, vitals,
 *   assessmentNotes, clinicalImpression, followUpDate, followUpInstructions
 */
public class ClinicalApprovalRequest {

    @NotNull(message = "approvedFields must not be null")
    private Set<String> approvedFields = new HashSet<>();

    public ClinicalApprovalRequest() {}

    public Set<String> getApprovedFields() { return approvedFields; }
    public void setApprovedFields(Set<String> approvedFields) {
        this.approvedFields = approvedFields != null ? approvedFields : new HashSet<>();
    }
}
