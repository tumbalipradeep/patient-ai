package com.patientcase.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Incoming request from the browser for an AI chat turn.
 * Sent as JSON to POST /api/ai/chat.
 */
public class AiChatRequest {

    @NotNull(message = "encounterId is required")
    private Long encounterId;

    /** Full conversation history so far: alternating user/assistant messages. */
    private List<Message> conversationHistory = new ArrayList<>();

    @Size(max = 4000, message = "userMessage must not exceed 4000 characters")
    private String userMessage;

    /**
     * Optional server-injected summary of facts already collected in this session.
     * Helps the AI avoid re-asking for information already provided.
     * Never sent from the browser — populated by the server from the session history.
     * Max 2000 chars to limit token usage.
     */
    @Size(max = 2000)
    private String knownFacts;

    public Long getEncounterId() { return encounterId; }
    public void setEncounterId(Long encounterId) { this.encounterId = encounterId; }

    public List<Message> getConversationHistory() { return conversationHistory; }
    public void setConversationHistory(List<Message> conversationHistory) {
        this.conversationHistory = conversationHistory != null ? conversationHistory : new ArrayList<>();
    }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }

    public String getKnownFacts() { return knownFacts; }
    public void setKnownFacts(String knownFacts) { this.knownFacts = knownFacts; }

    /** A single turn in the conversation. */
    public static class Message {
        private String role;   // "user" or "assistant"
        private String content;

        public Message() {}
        public Message(String role, String content) { this.role = role; this.content = content; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
