package com.patientcase.integration;

/**
 * Integration status for the ABDM/FHIR/HIS boundary.
 *
 * DEFAULT (not configured / sandbox): the system runs on local clinical data
 * with no live external exchange. ABHA-ready FHIR projections are available,
 * but no real ABDM/HIS participant is contacted.
 */
public enum IntegrationStatus {
    /** No external credentials/connectivity configured — runs on local data only. */
    NOT_CONFIGURED,
    /** A live integration is configured and operational. */
    CONNECTED,
    /** A live integration is configured but currently unavailable. */
    DEGRADED,
    /** Integration explicitly disabled by configuration. */
    DISABLED
}