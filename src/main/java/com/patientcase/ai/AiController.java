package com.patientcase.ai;

import com.patientcase.encounter.Encounter;
import com.patientcase.encounter.EncounterService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoint for the AI-assisted case-taking chat.
 *
 * POST /api/ai/chat
 * - Requires authentication (Spring Security enforces this via SecurityConfig).
 * - Validates that the encounterId exists.
 * - Verifies the authenticated user is the assigned clinician or has ADMIN role.
 * - Persists each conversation turn to the server-side AiIntakeSession.
 * - When the AI returns complete=true, saves the validated draft and signals draftReady.
 * - Never returns the AI API key, provider details, or stack traces to the caller.
 * - Never logs patient conversation content.
 *
 * CSRF: /api/** is exempt from CSRF in SecurityConfig; the header token is included
 * defensively by the client anyway.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiChatService aiService;
    private final EncounterService encounterService;
    private final AiSessionService sessionService;

    public AiController(AiChatService aiService,
                        EncounterService encounterService,
                        AiSessionService sessionService) {
        this.aiService = aiService;
        this.encounterService = encounterService;
        this.sessionService = sessionService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            @Valid @RequestBody AiChatRequest request,
            Authentication authentication) {

        // ---- Validate encounter exists ----
        Encounter encounter;
        try {
            encounter = encounterService.findById(request.getEncounterId());
        } catch (com.patientcase.common.ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(AiChatResponse.error("Encounter not found."));
        }

        // ---- Authorize: assigned clinician or ADMIN ----
        if (!isAuthorized(authentication, encounter)) {
            log.warn("Unauthorized AI chat attempt by user '{}' for encounter {}",
                    authentication.getName(), request.getEncounterId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(AiChatResponse.error("You are not authorized to access this encounter."));
        }

        // ---- Basic input guard ----
        String userMessage = request.getUserMessage();
        if (userMessage == null || userMessage.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(AiChatResponse.error("Message must not be empty."));
        }

        String username = authentication.getName();
        Long encounterId = request.getEncounterId();

        // ---- Ensure session exists; use server-side history as the authoritative source ----
        sessionService.getOrCreateSession(encounterId, username, isAdmin(authentication));
        List<AiChatRequest.Message> serverHistory = sessionService.getConversationHistory(encounterId);

        // ---- Call the AI provider ----
        AiChatResponse response = aiService.chat(serverHistory, userMessage);

        // ---- Handle disabled / error responses without persisting ----
        if (response.isDisabled()) {
            return ResponseEntity.ok(response);
        }
        if (response.getError() != null) {
            return ResponseEntity.ok(response);
        }

        // ---- Persist conversation turn ----
        String assistantReply = response.getReply();
        try {
            sessionService.appendMessages(encounterId, userMessage, assistantReply,
                    username, isAdmin(authentication));
        } catch (Exception e) {
            // Session persistence failure must not break the chat response
            log.warn("Failed to persist AI chat messages for encounter {}: {}",
                    encounterId, e.getClass().getSimpleName());
        }

        // ---- Handle complete=true: save validated draft ----
        if (response.isComplete() && response.getStructuredData() != null) {
            try {
                sessionService.saveDraft(encounterId, response.getStructuredData());
                // Signal to the client that the server-side draft is ready for review
                return ResponseEntity.ok(response.withDraftReady());
            } catch (AiDraftValidator.AiDraftValidationException e) {
                // AI produced unsafe structured output — return as plain reply, do not persist
                log.warn("AI structured output failed safety validation for encounter {}: {}",
                        encounterId, e.getMessage());
                return ResponseEntity.ok(
                        AiChatResponse.reply(
                                assistantReply != null ? assistantReply :
                                "I have gathered information but the draft could not be verified. " +
                                "Please proceed with manual case-taking."));
            } catch (Exception e) {
                log.warn("Unexpected error saving AI draft for encounter {}: {}",
                        encounterId, e.getClass().getSimpleName());
                return ResponseEntity.ok(response);
            }
        }

        return ResponseEntity.ok(response);
    }

    private boolean isAuthorized(Authentication auth, Encounter encounter) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) return true;
        return encounter.getClinician().getUsername().equals(auth.getName());
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
