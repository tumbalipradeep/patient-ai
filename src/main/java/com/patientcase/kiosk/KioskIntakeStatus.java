package com.patientcase.kiosk;

public enum KioskIntakeStatus {
    /** Patient is answering the conversational intake questions. */
    IN_PROGRESS,
    /** AI has produced a validated draft; patient is on the summary step. */
    DRAFT_READY,
    /** Patient submitted the intake for clinician review. */
    SUBMITTED,
    /** A clinician accepted the intake and an encounter was created. */
    ACCEPTED,
    /** A clinician rejected/discarded the intake. */
    REJECTED
}