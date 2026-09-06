package com.patientcase.api.kiosk;

import com.patientcase.ai.AiChatRequest;
import com.patientcase.ai.AiChatResponse;
import com.patientcase.ai.AiChatService;
import com.patientcase.ai.AiDraftValidator;
import com.patientcase.kiosk.KioskIntake;
import com.patientcase.kiosk.KioskIntakeService;
import com.patientcase.kiosk.KioskIntakeStatus;
import com.patientcase.patient.Patient;
import com.patientcase.user.User;
import com.patientcase.user.UserRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * REST endpoint for the patient-facing AI intake chat.
 *
 * POST /api/kiosk/chat
 * - Requires PATIENT role (SecurityConfig) — no staff account can call it.
 * - Ownership is enforced server-side: only the intake's own patient may chat.
 * - Conversation history is read from the server-side KioskIntake (never trusted
 *   from the browser), so turns cannot be fabricated or replayed across patients.
 * - When the AI returns complete=true, the validated draft is persisted via
 *   KioskIntakeService.saveDraft (status DRAFT_READY, red flags recorded).
 * - Never returns the AI API key, provider details, or stack traces.
 * - Never logs patient conversation content.
 */
@RestController
@RequestMapping("/api/kiosk")
public class KioskApiController {

    private static final Logger log = LoggerFactory.getLogger(KioskApiController.class);

    private final AiChatService aiService;
    private final KioskIntakeService intakeService;
    private final UserRepository userRepository;

    public KioskApiController(AiChatService aiService,
                              KioskIntakeService intakeService,
                              UserRepository userRepository) {
        this.aiService = aiService;
        this.intakeService = intakeService;
        this.userRepository = userRepository;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            @Valid @RequestBody KioskChatRequest request,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AiChatResponse.error("Please sign in to continue."));
        }

        // ---- Resolve the authenticated patient ----
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(AiChatResponse.error("Account not found."));
        }
        Patient patient;
        try {
            patient = intakeService.requirePatientForUser(user.getId());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(AiChatResponse.error("No patient record is linked to this account."));
        }

        // ---- Ownership + state check ----
        KioskIntake intake;
        try {
            intake = intakeService.requireOwnedIntake(request.getIntakeId(), patient.getId());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(AiChatResponse.error("Intake not found."));
        }
        if (intake.getStatus() != KioskIntakeStatus.IN_PROGRESS) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(AiChatResponse.error(
                            "This intake is no longer accepting responses."));
        }
        if (intake.getConsent() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(AiChatResponse.error(
                            "Consent must be granted before starting the intake."));
        }

        String userMessage = request.getUserMessage();
        if (userMessage == null || userMessage.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(AiChatResponse.error("Message must not be empty."));
        }

        // ---- Official history from server; add a context hint like the clinician flow ----
        List<AiChatRequest.Message> serverHistory =
                intakeService.getConversationHistory(request.getIntakeId(), patient.getId());
        List<AiChatRequest.Message> historyWithContext = buildHistoryWithContext(serverHistory);

        AiChatResponse response = aiService.chat(historyWithContext, userMessage);

        if (response.isDisabled() || response.getError() != null) {
            return ResponseEntity.ok(response);
        }

        // ---- Persist conversation turn ----
        String assistantReply = response.getReply();
        try {
            intakeService.appendMessages(request.getIntakeId(), patient.getId(),
                    userMessage, assistantReply);
        } catch (Exception e) {
            log.warn("Failed to persist kiosk chat messages for intake {}: {}",
                    request.getIntakeId(), e.getClass().getSimpleName());
        }

        // ---- Complete: persist the validated draft ----
        if (response.isComplete() && response.getStructuredData() != null) {
            try {
                intakeService.saveDraft(request.getIntakeId(), patient.getId(),
                        response.getStructuredData(), response.isUrgentFlag());
                return ResponseEntity.ok(response.withDraftReady());
            } catch (AiDraftValidator.AiDraftValidationException e) {
                log.warn("Kiosk AI structured output failed safety validation for intake {}: {}",
                        request.getIntakeId(), e.getMessage());
                return ResponseEntity.ok(AiChatResponse.reply(
                        assistantReply != null ? assistantReply :
                        "I have gathered enough information, but the summary could not be verified. " +
                        "Please review your answers on the next screen."));
            } catch (Exception e) {
                log.warn("Unexpected error saving kiosk AI draft for intake {}: {}",
                        request.getIntakeId(), e.getClass().getSimpleName());
                return ResponseEntity.ok(response);
            }
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Prepend a synthetic context note (never persisted) so the AI builds on
     * what it has already collected instead of repeating questions.
     */
    private List<AiChatRequest.Message> buildHistoryWithContext(
            List<AiChatRequest.Message> history) {
        if (history == null || history.isEmpty()) return history;
        long patientTurns = history.stream()
                .filter(m -> "user".equals(m.getRole()))
                .count();
        if (patientTurns == 0) return history;
        String contextNote = "[Context: " + patientTurns
                + " patient response(s) already recorded. "
                + "Review the conversation above and ask only about information not yet provided.]";
        List<AiChatRequest.Message> withContext = new ArrayList<>(history.size() + 1);
        withContext.add(new AiChatRequest.Message("system", contextNote));
        withContext.addAll(history);
        return withContext;
    }
}