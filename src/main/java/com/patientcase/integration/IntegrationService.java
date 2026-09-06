package com.patientcase.integration;

import com.patientcase.integration.fhir.FhirResources;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ABDM/HIS integration boundary.
 *
 * This service deliberately does NOT claim live ABDM/ABHA connectivity unless
 * integration credentials and connectivity are actually configured. By default
 * the system is ABHA/FHIR-READY: it can produce standard FHIR R4 projections
 * of clinical records, but no external system is contacted.
 *
 * A future live adapter can be added behind the same surface (exportBundle,
 * linkAbha) without changing callers.
 */
@Service
public class IntegrationService {

    @Value("${app.integration.mode:disabled}")
    private String mode;

    @Value("${app.integration.abdm.endpoint:}")
    private String abdmEndpoint;

    public IntegrationStatus status() {
        if ("disabled".equalsIgnoreCase(mode)) return IntegrationStatus.DISABLED;
        boolean hasCredentials = abdmEndpoint != null && !abdmEndpoint.isBlank();
        if (!hasCredentials) return IntegrationStatus.NOT_CONFIGURED;
        return IntegrationStatus.DEGRADED; // no live connectivity check is performed without credentials
    }

    /** Human-readable boundary state for admin/status pages. */
    public String statusLabel() {
        return switch (status()) {
            case CONNECTED -> "ABDM/HIS integration active";
            case DEGRADED -> "ABDM/HIS endpoint configured but connectivity not verified";
            case DISABLED -> "External integration disabled by configuration";
            case NOT_CONFIGURED -> "ABHA-ready; no ABDM/HIS credentials configured";
        };
    }

    public boolean isLive() {
        return status() == IntegrationStatus.CONNECTED;
    }

    /**
     * Build a FHIR R4 bundle of the clinical record for export. Safe to call
     * at any time — local projections only, no external side effects.
     */
    public FhirResources.Bundle buildBundle(FhirResources.Patient patient,
                                            FhirResources.Encounter encounter,
                                            List<Object> clinical) {
        List<Object> resources = new java.util.ArrayList<>();
        if (patient != null) resources.add(patient);
        if (encounter != null) resources.add(encounter);
        if (clinical != null) resources.addAll(clinical);
        return new FhirResources.Bundle(resources);
    }

    /**
     * Placeholder for linking an ABHA (Ayushman Bharat Health Account) number.
     * Returns false unless a real linking implementation is configured,
     * so callers never report a successful link that did not happen.
     */
    public boolean linkAbha(String patientNumber, String abhaNumber) {
        return false;
    }
}