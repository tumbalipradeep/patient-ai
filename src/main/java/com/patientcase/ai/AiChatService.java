package com.patientcase.ai;

import java.util.List;

/**
 * Contract for the AI chat assistant.
 * Extracted as an interface so tests can use JDK dynamic proxies (Mockito without Byte Buddy).
 */
public interface AiChatService {

    /**
     * Process one chat turn given the prior conversation history and the new user message.
     * Implementations must never throw — all error paths return an {@link AiChatResponse}.
     */
    AiChatResponse chat(List<AiChatRequest.Message> history, String userMessage);
}
