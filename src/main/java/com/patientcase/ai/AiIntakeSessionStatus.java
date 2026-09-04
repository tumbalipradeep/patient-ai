package com.patientcase.ai;

/**
 * Lifecycle states for an AI intake session.
 *
 * IN_PROGRESS  — conversation is ongoing; no complete draft yet.
 * DRAFT_READY  — AI produced a structured draft; awaiting clinician review/approval.
 * APPLIED      — clinician approved and applied selected fields to the encounter (terminal).
 * DISCARDED    — clinician discarded the draft; session is inactive (terminal).
 */
public enum AiIntakeSessionStatus {
    IN_PROGRESS,
    DRAFT_READY,
    APPLIED,
    DISCARDED
}
