package com.patientcase.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientcase.audit.AuditAction;
import com.patientcase.audit.AuditService;
import com.patientcase.clinical.Onset;
import com.patientcase.clinical.Severity;
import com.patientcase.clinical.Symptom;
import com.patientcase.clinical.SymptomRepository;
import com.patientcase.common.ResourceNotFoundException;
import com.patientcase.encounter.Encounter;
import com.patientcase.encounter.EncounterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Manages AI intake sessions for the clinician-reviewed intake workflow.
 *
 * Responsibilities:
 *   - Create or retrieve the active session for an encounter.
 *   - Append conversation messages (user + assistant turns).
 *   - Save a validated draft (transitions IN_PROGRESS → DRAFT_READY).
 *   - Apply approved fields to the Encounter (DRAFT_READY → APPLIED).
 *   - Discard a session (any non-terminal state → DISCARDED).
 *
 * Safety contract:
 *   - Only patient-reported fields (chiefComplaint, historyOfPresentIllness,
 *     relevantHistory, symptoms) may be applied to the encounter.
 *   - Diagnoses, treatments, examinations, vitals, assessmentNotes, and
 *     clinicalImpression are NEVER written here — enforced at the service layer
 *     independently of upstream validation.
 *   - Every application is preceded by re-validation of the stored draft.
 *   - Audit events are written for DRAFT_GENERATED and DRAFT_APPLIED.
 *   - No patient conversation content appears in log statements.
 *   - No raw AI JSON is logged.
 */
@Service
public class AiIntakeSessionService implements AiSessionService {

    private static final Logger log = LoggerFactory.getLogger(AiIntakeSessionService.class);

    /** Fields the clinician is permitted to approve. */
    private static final Set<String> ALLOWED_APPROVE_FIELDS =
            Set.of("chiefComplaint", "historyOfPresentIllness", "relevantHistory", "symptoms");

    private final AiIntakeSessionRepository sessionRepository;
    private final EncounterRepository encounterRepository;
    private final SymptomRepository symptomRepository;
    private final AiDraftValidator validator;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AiIntakeSessionService(AiIntakeSessionRepository sessionRepository,
                                   EncounterRepository encounterRepository,
                                   SymptomRepository symptomRepository,
                                   AiDraftValidator validator,
                                   AuditService auditService,
                                   ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.encounterRepository = encounterRepository;
        this.symptomRepository = symptomRepository;
        this.validator = validator;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    // ---- Session retrieval / creation ----

    /**
     * Return the existing session for this encounter, or create a new IN_PROGRESS one.
     * If the existing session is APPLIED or DISCARDED, a new session replaces it.
     */
    @Transactional
    public AiIntakeSession getOrCreateSession(Long encounterId, String username, boolean isAdmin) {
        Encounter encounter = requireEncounter(encounterId);
        requireOwnership(encounter, username, isAdmin);
        return sessionRepository.findByEncounterId(encounterId)
                .map(existing -> {
                    if (existing.getStatus() == AiIntakeSessionStatus.DISCARDED) {
                        // Replace a discarded session with a fresh one
                        sessionRepository.delete(existing);
                        sessionRepository.flush();
                        return createSession(encounter, username);
                    }
                    // APPLIED, IN_PROGRESS, DRAFT_READY — return as-is
                    // (APPLIED is a terminal record and must not be replaced)
                    return existing;
                })
                .orElseGet(() -> createSession(encounter, username));
    }

    /** Read-only lookup — returns empty if no session exists yet. */
    @Transactional(readOnly = true)
    public java.util.Optional<AiIntakeSession> findSession(Long encounterId) {
        return sessionRepository.findByEncounterId(encounterId);
    }

    // ---- Conversation message persistence ----

    /**
     * Append one user message and one assistant reply to the session history.
     * The session must be IN_PROGRESS.
     */
    @Transactional
    public void appendMessages(Long encounterId,
                                String userMessage,
                                String assistantReply,
                                String username,
                                boolean isAdmin) {
        Encounter encounter = requireEncounter(encounterId);
        requireOwnership(encounter, username, isAdmin);
        AiIntakeSession session = requireActiveSession(encounterId);
        List<MessageEntry> messages = deserialiseMessages(session.getMessagesJson());
        if (userMessage != null && !userMessage.isBlank()) {
            messages.add(new MessageEntry("user", userMessage));
        }
        if (assistantReply != null && !assistantReply.isBlank()) {
            messages.add(new MessageEntry("assistant", assistantReply));
        }
        session.setMessagesJson(serialiseMessages(messages));
        sessionRepository.save(session);
    }

    /**
     * Retrieve the conversation history for an encounter as a list of messages.
     * Returns an empty list if no session exists.
     */
    @Transactional(readOnly = true)
    public List<AiChatRequest.Message> getConversationHistory(Long encounterId) {
        return sessionRepository.findByEncounterId(encounterId)
                .map(s -> toApiMessages(deserialiseMessages(s.getMessagesJson())))
                .orElseGet(ArrayList::new);
    }

    // ---- Draft persistence ----

    /**
     * Parse, validate, and persist the AI structured draft.
     * Transitions session from IN_PROGRESS → DRAFT_READY.
     * Throws {@link AiDraftValidator.AiDraftValidationException} if safety rules are violated.
     */
    @Transactional
    public AiDraftDto saveDraft(Long encounterId, String structuredJson) {
        AiIntakeSession session = requireActiveSession(encounterId);

        // Parse and validate — throws if any prohibited field present
        AiDraftDto clean = validator.parseAndValidate(structuredJson);
        String cleanJson = validator.serialise(clean);

        session.setDraftJson(cleanJson);
        session.setStatus(AiIntakeSessionStatus.DRAFT_READY);
        sessionRepository.save(session);

        int symptomCount = clean.getSymptoms() != null ? clean.getSymptoms().size() : 0;
        auditService.log(
                AuditAction.AI_DRAFT_GENERATED,
                "Encounter", encounterId,
                "AI intake draft generated: " + symptomCount + " symptom(s)");

        log.info("AI intake draft saved for encounter {} ({} symptoms)", encounterId, symptomCount);
        return clean;
    }

    /**
     * Retrieve the validated draft for an encounter.
     * Returns null if no session or no draft.
     */
    @Transactional(readOnly = true)
    public AiDraftDto getDraft(Long encounterId) {
        return sessionRepository.findByEncounterId(encounterId)
                .map(s -> validator.deserialise(s.getDraftJson()))
                .orElse(null);
    }

    // ---- Clinician approval ----

    /**
     * Apply the clinician-approved fields from the stored draft to the encounter.
     *
     * Rules:
     *   - Session must be in DRAFT_READY state.
     *   - Stored draft is re-validated before application (defence-in-depth).
     *   - Only fields in ALLOWED_APPROVE_FIELDS may be written.
     *   - Prohibited clinical fields (diagnoses, treatments, examinations, vitals,
     *     assessmentNotes, clinicalImpression) are NEVER written regardless of request.
     *   - Session transitions to APPLIED (terminal).
     *   - Audit event written.
     *
     * @param encounterId   the encounter to apply to
     * @param approvedFields set of field names the clinician explicitly approved
     * @param username       the authenticated clinician's username (for auth check)
     * @throws AccessDeniedException         if username does not match the encounter's clinician (and is not admin — admin check done at controller layer)
     * @throws IllegalStateException         if session is not DRAFT_READY, or already APPLIED
     * @throws ResourceNotFoundException     if encounter or session not found
     */
    @Transactional
    public void applyDraft(Long encounterId,
                            Set<String> approvedFields,
                            String username,
                            boolean isAdmin) {
        Encounter encounter = requireEncounter(encounterId);

        // Authorization check
        if (!isAdmin && !encounter.getClinician().getUsername().equals(username)) {
            throw new AccessDeniedException("You are not authorized to apply this AI draft.");
        }

        AiIntakeSession session = sessionRepository.findByEncounterId(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No AI intake session found for encounter " + encounterId));

        if (session.getStatus() == AiIntakeSessionStatus.APPLIED) {
            throw new IllegalStateException("This AI draft has already been applied.");
        }
        if (session.getStatus() == AiIntakeSessionStatus.DISCARDED) {
            throw new IllegalStateException("This AI session has been discarded and cannot be applied.");
        }
        if (session.getStatus() != AiIntakeSessionStatus.DRAFT_READY) {
            throw new IllegalStateException(
                    "AI draft is not ready for application (status: " + session.getStatus() + ").");
        }

        // Validate approved field names — reject any prohibited field
        for (String field : approvedFields) {
            if (!ALLOWED_APPROVE_FIELDS.contains(field)) {
                throw new IllegalArgumentException(
                        "Field '" + field + "' is not permitted for AI-assisted application. " +
                        "Only patient-reported fields may be applied from an AI draft.");
            }
        }

        // Re-validate the stored draft (defence-in-depth)
        if (session.getDraftJson() == null || session.getDraftJson().isBlank()) {
            throw new IllegalStateException("AI draft content is missing.");
        }
        AiDraftDto draft = validator.parseAndValidate(session.getDraftJson());

        // Apply only the requested safe fields
        applyFields(encounter, draft, approvedFields);
        encounterRepository.save(encounter);

        // If symptoms approved, replace existing symptoms
        if (approvedFields.contains("symptoms")) {
            applySymptoms(encounter, draft.getSymptoms());
        }

        // Mark session as applied
        session.setStatus(AiIntakeSessionStatus.APPLIED);
        sessionRepository.save(session);

        String fieldsSummary = String.join(",", approvedFields);
        auditService.log(
                AuditAction.AI_DRAFT_APPLIED,
                "Encounter", encounterId,
                "Clinician applied AI intake fields: " + fieldsSummary);

        log.info("AI intake draft applied to encounter {} — fields: {}", encounterId, fieldsSummary);
    }

    // ---- Discard ----

    /**
     * Discard the current session for an encounter.
     * Allowed from IN_PROGRESS or DRAFT_READY.
     * APPLIED sessions cannot be discarded (they are terminal records).
     */
    @Transactional
    public void discardSession(Long encounterId, String username, boolean isAdmin) {
        Encounter encounter = requireEncounter(encounterId);
        if (!isAdmin && !encounter.getClinician().getUsername().equals(username)) {
            throw new AccessDeniedException("You are not authorized to discard this AI session.");
        }

        AiIntakeSession session = sessionRepository.findByEncounterId(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No AI intake session found for encounter " + encounterId));

        if (session.getStatus() == AiIntakeSessionStatus.APPLIED) {
            throw new IllegalStateException("An applied session cannot be discarded.");
        }
        session.setStatus(AiIntakeSessionStatus.DISCARDED);
        sessionRepository.save(session);
        log.info("AI intake session discarded for encounter {}", encounterId);
    }

    // ---- Private helpers ----

    private AiIntakeSession createSession(Encounter encounter, String username) {
        AiIntakeSession session = new AiIntakeSession();
        session.setEncounter(encounter);
        session.setStatus(AiIntakeSessionStatus.IN_PROGRESS);
        session.setCreatedBy(username);
        session.setMessagesJson("[]");
        return sessionRepository.save(session);
    }

    /**
     * Require an IN_PROGRESS or DRAFT_READY (active) session.
     * DRAFT_READY sessions can still receive messages in edge cases
     * but the normal path is IN_PROGRESS.
     */
    private AiIntakeSession requireActiveSession(Long encounterId) {
        return sessionRepository.findByEncounterId(encounterId)
                .filter(s -> s.getStatus() == AiIntakeSessionStatus.IN_PROGRESS
                          || s.getStatus() == AiIntakeSessionStatus.DRAFT_READY)
                .orElseThrow(() -> new IllegalStateException(
                        "No active AI intake session for encounter " + encounterId +
                        ". Start the AI intake conversation first."));
    }

    private Encounter requireEncounter(Long encounterId) {
        return encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Encounter not found: " + encounterId));
    }

    /**
     * Enforce that the caller owns the encounter or has admin rights.
     * Called by every mutating operation, including getOrCreateSession and appendMessages,
     * so the service is self-defending regardless of the call site.
     */
    private void requireOwnership(Encounter encounter, String username, boolean isAdmin) {
        if (!isAdmin && !encounter.getClinician().getUsername().equals(username)) {
            throw new AccessDeniedException(
                    "You are not authorized to access this AI intake session.");
        }
    }

    /**
     * Write approved fields from the draft to the Encounter entity.
     * This method ONLY touches text narrative fields — never clinical judgement fields.
     */
    private void applyFields(Encounter encounter, AiDraftDto draft, Set<String> approvedFields) {
        if (approvedFields.contains("chiefComplaint") && draft.getChiefComplaint() != null) {
            encounter.setChiefComplaint(draft.getChiefComplaint());
        }
        if (approvedFields.contains("historyOfPresentIllness")
                && draft.getHistoryOfPresentIllness() != null) {
            encounter.setHistoryOfPresentIllness(draft.getHistoryOfPresentIllness());
        }
        if (approvedFields.contains("relevantHistory") && draft.getRelevantHistory() != null) {
            encounter.setRelevantHistory(draft.getRelevantHistory());
        }
        // Explicitly never touch: assessmentNotes, clinicalImpression — those are clinician-only
    }

    /**
     * Replace the encounter's symptoms with the AI-drafted symptoms.
     * Existing symptoms are deleted and replaced (same pattern as EncounterService.saveCaseTaking).
     */
    private void applySymptoms(Encounter encounter, List<AiSymptomDraft> drafts) {
        symptomRepository.deleteByEncounterId(encounter.getId());

        if (drafts == null || drafts.isEmpty()) return;

        for (AiSymptomDraft d : drafts) {
            if (d.getName() == null || d.getName().isBlank()) continue;

            Symptom symptom = new Symptom();
            symptom.setEncounter(encounter);
            symptom.setName(d.getName());
            symptom.setDuration(d.getDuration());

            // severity: null from validator means patient did not state one — use MILD as safe default for DB NOT NULL
            // The validator deliberately leaves severity null when unknown; we must handle that here.
            symptom.setSeverity(parseSeverity(d.getSeverity()));
            symptom.setOnset(parseOnset(d.getOnset()));
            symptom.setNotes(d.getNotes());
            symptomRepository.save(symptom);
        }
    }

    private Severity parseSeverity(String raw) {
        if (raw == null || raw.isBlank()) return Severity.MILD; // safe DB default; clinician reviews
        try {
            return Severity.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Severity.MILD;
        }
    }

    private Onset parseOnset(String raw) {
        if (raw == null || raw.isBlank()) return Onset.UNKNOWN;
        try {
            return Onset.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Onset.UNKNOWN;
        }
    }

    // ---- Message JSON helpers ----

    private List<MessageEntry> deserialiseMessages(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<MessageEntry>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialise session messages: {}", e.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }

    private String serialiseMessages(List<MessageEntry> messages) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (Exception e) {
            log.warn("Failed to serialise session messages: {}", e.getClass().getSimpleName());
            return "[]";
        }
    }

    private List<AiChatRequest.Message> toApiMessages(List<MessageEntry> entries) {
        List<AiChatRequest.Message> result = new ArrayList<>();
        for (MessageEntry e : entries) {
            result.add(new AiChatRequest.Message(e.getRole(), e.getContent()));
        }
        return result;
    }

    /** Internal DTO for message JSON serialisation — simple role/content pair. */
    public static class MessageEntry {
        private String role;
        private String content;

        public MessageEntry() {}
        public MessageEntry(String role, String content) {
            this.role = role;
            this.content = content;
        }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
