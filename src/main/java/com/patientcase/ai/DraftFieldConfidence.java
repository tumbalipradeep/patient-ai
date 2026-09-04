package com.patientcase.ai;

/**
 * Indicates the evidential basis of a field extracted from the AI conversation.
 *
 * PATIENT_REPORTED — patient explicitly stated this information.
 * AI_INFERRED      — AI inferred this from context; requires extra scrutiny.
 * MISSING          — information was not collected during the conversation.
 */
public enum DraftFieldConfidence {
    PATIENT_REPORTED,
    AI_INFERRED,
    MISSING
}
