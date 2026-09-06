package com.patientcase.api.kiosk;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Incoming request for the patient kiosk chat turn.
 * POST /api/kiosk/chat (PATIENT role only).
 */
public class KioskChatRequest {

    @NotNull(message = "intakeId is required")
    private Long intakeId;

    @Size(max = 4000, message = "userMessage must not exceed 4000 characters")
    private String userMessage;

    public Long getIntakeId() { return intakeId; }
    public void setIntakeId(Long intakeId) { this.intakeId = intakeId; }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
}