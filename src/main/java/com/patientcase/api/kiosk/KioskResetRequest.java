package com.patientcase.api.kiosk;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/kiosk/chat/reset (PATIENT role only).
 *
 * Clears the server-side AI conversation history for an intake that is still
 * IN_PROGRESS. Requires the same ownership + consent guards as the chat turn.
 */
public class KioskResetRequest {

    @NotNull(message = "intakeId is required")
    private Long intakeId;

    public Long getIntakeId() { return intakeId; }
    public void setIntakeId(Long intakeId) { this.intakeId = intakeId; }
}