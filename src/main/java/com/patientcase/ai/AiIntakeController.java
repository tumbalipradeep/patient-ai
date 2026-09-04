package com.patientcase.ai;

import com.patientcase.common.ResourceNotFoundException;
import com.patientcase.encounter.Encounter;
import com.patientcase.encounter.EncounterService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Thymeleaf controller for the AI intake workflow pages.
 *
 * All endpoints under /encounters/{id}/ai-intake/** require authentication
 * (enforced by SecurityConfig URL rule for /encounters/**).
 *
 * Fine-grained authorization (assigned clinician or ADMIN) is enforced
 * per-method, with 403 thrown via AccessDeniedException → GlobalExceptionHandler.
 * Not-found throws ResourceNotFoundException → GlobalExceptionHandler → 404.
 *
 * GET  /encounters/{id}/ai-intake          — chat UI page
 * GET  /encounters/{id}/ai-intake/draft    — clinician draft review page
 * POST /encounters/{id}/ai-intake/apply    — clinician approves and applies draft
 * POST /encounters/{id}/ai-intake/discard  — clinician discards draft / session
 */
@Controller
public class AiIntakeController {

    private static final Logger log = LoggerFactory.getLogger(AiIntakeController.class);

    private final EncounterService encounterService;
    private final AiSessionService sessionService;

    public AiIntakeController(EncounterService encounterService,
                               AiSessionService sessionService) {
        this.encounterService = encounterService;
        this.sessionService = sessionService;
    }

    // ---- GET /encounters/{id}/ai-intake ----

    @GetMapping("/encounters/{id}/ai-intake")
    public String aiIntakePage(@PathVariable Long id,
                                Authentication authentication,
                                Model model) {
        Encounter encounter = requireEncounter(id);
        requireAuthorized(authentication, encounter);

        // Load or create session so the page always has an active session
        sessionService.getOrCreateSession(id, authentication.getName(), isAdmin(authentication));

        // Expose session status so JS can restore history / show draft-ready banner
        sessionService.findSession(id).ifPresent(s -> {
            model.addAttribute("sessionStatus", s.getStatus().name());
            model.addAttribute("sessionHasDraft",
                    s.getStatus() == AiIntakeSessionStatus.DRAFT_READY
                    || s.getStatus() == AiIntakeSessionStatus.APPLIED);
        });

        model.addAttribute("encounter", encounter);
        return "encounters/ai-intake";
    }

    // ---- GET /encounters/{id}/ai-intake/draft ----

    @GetMapping("/encounters/{id}/ai-intake/draft")
    public String draftReviewPage(@PathVariable Long id,
                                   Authentication authentication,
                                   Model model) {
        Encounter encounter = requireEncounter(id);
        requireAuthorized(authentication, encounter);

        AiIntakeSession session = sessionService.findSession(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No AI intake session found for encounter " + id));

        if (session.getStatus() == AiIntakeSessionStatus.IN_PROGRESS
                || session.getStatus() == AiIntakeSessionStatus.DISCARDED) {
            // No draft yet (or session was discarded) — redirect back to the intake chat
            return "redirect:/encounters/" + id + "/ai-intake";
        }

        AiDraftDto draft = sessionService.getDraft(id);
        if (draft == null) {
            throw new ResourceNotFoundException(
                    "AI intake draft not found for encounter " + id);
        }

        model.addAttribute("encounter", encounter);
        model.addAttribute("draft", draft);
        model.addAttribute("session", session);
        model.addAttribute("alreadyApplied",
                session.getStatus() == AiIntakeSessionStatus.APPLIED);
        return "encounters/ai-intake-draft";
    }

    // ---- POST /encounters/{id}/ai-intake/apply ----

    @PostMapping("/encounters/{id}/ai-intake/apply")
    public String applyDraft(@PathVariable Long id,
                              @Valid @ModelAttribute ClinicalApprovalRequest approvalRequest,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        Encounter encounter = requireEncounter(id);
        requireAuthorized(authentication, encounter);

        if (approvalRequest.getApprovedFields() == null
                || approvalRequest.getApprovedFields().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Please select at least one field to apply from the AI draft.");
            return "redirect:/encounters/" + id + "/ai-intake/draft";
        }

        try {
            boolean isAdmin = isAdmin(authentication);
            sessionService.applyDraft(id, approvalRequest.getApprovedFields(),
                    authentication.getName(), isAdmin);
            redirectAttributes.addFlashAttribute("successMessage",
                    "AI intake draft applied. Review the pre-filled information below " +
                    "and complete the case-taking form before saving.");
            return "redirect:/encounters/" + id + "/case-taking";
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/encounters/" + id + "/ai-intake/draft";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/encounters/" + id + "/ai-intake/draft";
        } catch (AiDraftValidator.AiDraftValidationException e) {
            log.warn("Draft re-validation failed on apply for encounter {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "The AI draft failed safety validation and cannot be applied. " +
                    "Please proceed with manual case-taking.");
            return "redirect:/encounters/" + id + "/ai-intake/draft";
        }
    }

    // ---- POST /encounters/{id}/ai-intake/discard ----

    @PostMapping("/encounters/{id}/ai-intake/discard")
    public String discardSession(@PathVariable Long id,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        Encounter encounter = requireEncounter(id);
        requireAuthorized(authentication, encounter);

        try {
            sessionService.discardSession(id, authentication.getName(), isAdmin(authentication));
            redirectAttributes.addFlashAttribute("infoMessage",
                    "AI intake session discarded. You can start a new session at any time.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/encounters/" + id + "/ai-intake";
    }

    // ---- Helpers ----

    private Encounter requireEncounter(Long id) {
        // ResourceNotFoundException propagates to GlobalExceptionHandler → 404
        return encounterService.findById(id);
    }

    private void requireAuthorized(Authentication auth, Encounter encounter) {
        if (!isAdmin(auth) && !encounter.getClinician().getUsername().equals(auth.getName())) {
            log.warn("Unauthorized AI intake access by '{}' for encounter {}",
                    auth.getName(), encounter.getId());
            throw new AccessDeniedException("You are not authorized to access this encounter.");
        }
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
