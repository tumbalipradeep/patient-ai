package com.patientcase.ai;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Contract for managing AI intake sessions.
 * Extracted as an interface so tests can use JDK dynamic proxies
 * (Mockito without Byte Buddy — compatible with Java 25).
 */
public interface AiSessionService {

    /** Create or retrieve the active session. The caller's username must be the encounter's clinician or isAdmin=true. */
    AiIntakeSession getOrCreateSession(Long encounterId, String username, boolean isAdmin);

    Optional<AiIntakeSession> findSession(Long encounterId);

    /** Append a conversation turn. The caller must be the encounter's clinician or isAdmin=true. */
    void appendMessages(Long encounterId, String userMessage, String assistantReply,
                        String username, boolean isAdmin);

    List<AiChatRequest.Message> getConversationHistory(Long encounterId);

    AiDraftDto saveDraft(Long encounterId, String structuredJson);

    AiDraftDto getDraft(Long encounterId);

    void applyDraft(Long encounterId, Set<String> approvedFields,
                    String username, boolean isAdmin);

    void discardSession(Long encounterId, String username, boolean isAdmin);
}
