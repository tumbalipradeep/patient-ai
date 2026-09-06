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

    /**
     * Client-generated unique id for this conversation turn, used ONLY for
     * idempotency: a repeated request with the same id is answered from the
     * server's stored record instead of calling the AI provider again or
     * double-appending the conversation history. Never trusted for content.
     */
    @Size(max = 64, message = "clientTurnId must not exceed 64 characters")
    private String clientTurnId;

    public Long getIntakeId() { return intakeId; }
    public void setIntakeId(Long intakeId) { this.intakeId = intakeId; }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }

    public String getClientTurnId() { return clientTurnId; }
    public void setClientTurnId(String clientTurnId) { this.clientTurnId = clientTurnId; }
}