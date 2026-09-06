package com.patientcase.kiosk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientcase.ai.AiChatRequest;
import com.patientcase.ai.AiDraftDto;
import com.patientcase.ai.AiDraftValidator;
import com.patientcase.ai.AiSymptomDraft;
import com.patientcase.ai.DraftFieldConfidence;
import com.patientcase.audit.AuditAction;
import com.patientcase.audit.AuditService;
import com.patientcase.case_management.CasePriority;
import com.patientcase.case_management.CaseStatus;
import com.patientcase.case_management.PatientCase;
import com.patientcase.case_management.PatientCaseRepository;
import com.patientcase.clinical.Onset;
import com.patientcase.clinical.Severity;
import com.patientcase.clinical.Symptom;
import com.patientcase.clinical.SymptomRepository;
import com.patientcase.common.ResourceNotFoundException;
import com.patientcase.document.Document;
import com.patientcase.document.DocumentRepository;
import com.patientcase.encounter.Encounter;
import com.patientcase.encounter.EncounterRepository;
import com.patientcase.encounter.EncounterStatus;
import com.patientcase.patient.Patient;
import com.patientcase.patient.PatientRepository;
import com.patientcase.user.User;
import com.patientcase.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the patient-facing MediKiosk intake lifecycle.
 *
 * Flow: IN_PROGRESS -> DRAFT_READY -> SUBMITTED -> ACCEPTED | REJECTED.
 *
 * Safety contract:
 *   - Only the intake's own patient (or staff) may read/mutate the intake.
 *   - Drafts are re-validated via AiDraftValidator before persistence.
 *   - Red flags are patient-safety observations persisted for triage, never diagnoses.
 *   - Acceptance creates a real PatientCase + Encounter through the existing
 *     clinical schema and applies only patient-reported fields.
 *   - No patient conversation content is ever logged.
 */
@Service
public class KioskIntakeService {

    private static final Logger log = LoggerFactory.getLogger(KioskIntakeService.class);

    private final KioskIntakeRepository intakeRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final RedFlagRepository redFlagRepository;
    private final AyushAssessmentRepository ayushRepository;
    private final PatientCaseRepository caseRepository;
    private final EncounterRepository encounterRepository;
    private final SymptomRepository symptomRepository;
    private final DocumentRepository documentRepository;
    private final AiDraftValidator validator;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public KioskIntakeService(KioskIntakeRepository intakeRepository,
                              PatientRepository patientRepository,
                              UserRepository userRepository,
                              RedFlagRepository redFlagRepository,
                              AyushAssessmentRepository ayushRepository,
                              PatientCaseRepository caseRepository,
                              EncounterRepository encounterRepository,
                              SymptomRepository symptomRepository,
                              DocumentRepository documentRepository,
                              AiDraftValidator validator,
                              AuditService auditService,
                              ObjectMapper objectMapper,
                              JdbcTemplate jdbcTemplate) {
        this.intakeRepository = intakeRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.redFlagRepository = redFlagRepository;
        this.ayushRepository = ayushRepository;
        this.caseRepository = caseRepository;
        this.encounterRepository = encounterRepository;
        this.symptomRepository = symptomRepository;
        this.documentRepository = documentRepository;
        this.validator = validator;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---- Patient lookup ----

    @Transactional(readOnly = true)
    public Patient requirePatientForUser(Long userId) {
        return patientRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "No patient record is linked to this account."));
    }

    // ---- Session retrieval / creation ----

    @Transactional
    public KioskIntake getOrCreateActiveIntake(Long patientId, Long userId) {
        KioskIntake active = intakeRepository
                .findFirstByPatientIdAndStatusInOrderByCreatedAtDesc(patientId,
                        List.of(KioskIntakeStatus.IN_PROGRESS, KioskIntakeStatus.DRAFT_READY))
                .orElse(null);
        if (active != null) return active;

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + patientId));
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);

        KioskIntake intake = new KioskIntake();
        intake.setPatient(patient);
        intake.setUser(user);
        intake.setStatus(KioskIntakeStatus.IN_PROGRESS);
        intake.setMessagesJson("[]");
        KioskIntake saved = intakeRepository.save(intake);
        auditService.log(AuditAction.INTAKE_CREATED, "KioskIntake", saved.getId(), null);
        log.info("Kiosk intake {} created for patient {}", saved.getId(), patient.getPatientNumber());
        return saved;
    }

    @Transactional(readOnly = true)
    public KioskIntake requireOwnedIntake(Long intakeId, Long patientId) {
        KioskIntake intake = intakeRepository.findById(intakeId)
                .orElseThrow(() -> new ResourceNotFoundException("Intake not found: " + intakeId));
        if (!intake.getPatient().getId().equals(patientId)) {
            throw new AccessDeniedException("This intake does not belong to the current patient.");
        }
        return intake;
    }

    @Transactional(readOnly = true)
    public List<KioskIntake> findIntakesForPatient(Long patientId) {
        return intakeRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    // ---- Consent binding ----

    @Transactional
    public KioskIntake bindConsent(Long intakeId, Long patientId, com.patientcase.consent.Consent consent) {
        KioskIntake intake = requireOwnedIntake(intakeId, patientId);
        intake.setConsent(consent);
        return intakeRepository.save(intake);
    }

    // ---- Conversation persistence ----

    @Transactional
    public void appendMessages(Long intakeId, Long patientId, String userMessage, String assistantReply) {
        KioskIntake intake = requireOwnedIntake(intakeId, patientId);
        if (intake.getStatus() != KioskIntakeStatus.IN_PROGRESS) {
            throw new IllegalStateException("This intake is not accepting new responses.");
        }
        List<MessageEntry> messages = deserialiseMessages(intake.getMessagesJson());
        if (userMessage != null && !userMessage.isBlank()) {
            messages.add(new MessageEntry("user", userMessage));
        }
        if (assistantReply != null && !assistantReply.isBlank()) {
            messages.add(new MessageEntry("assistant", assistantReply));
        }
        intake.setMessagesJson(serialise(messages));
        intakeRepository.save(intake);
    }

    /**
     * Record one conversation turn and remember the client's turn id so a
     * duplicate submit of the same turn can be answered from the stored reply
     * without calling the AI provider or double-appending the history.
     *
     * @return true if this turn was newly recorded, false if it was a duplicate
     *         of the last recorded turn (and was therefore ignored).
     */
    @Transactional
    public boolean recordTurn(Long intakeId, Long patientId, String clientTurnId,
                              String userMessage, String assistantReply) {
        KioskIntake intake = requireOwnedIntake(intakeId, patientId);
        if (intake.getStatus() != KioskIntakeStatus.IN_PROGRESS) {
            throw new IllegalStateException("This intake is not accepting new responses.");
        }

        if (clientTurnId != null && !clientTurnId.isBlank()
                && clientTurnId.equals(intake.getLastClientTurnId())) {
            // Duplicate submit of the most recent turn — nothing new to record.
            return false;
        }

        List<MessageEntry> messages = deserialiseMessages(intake.getMessagesJson());
        if (userMessage != null && !userMessage.isBlank()) {
            messages.add(new MessageEntry("user", userMessage));
        }
        if (assistantReply != null && !assistantReply.isBlank()) {
            messages.add(new MessageEntry("assistant", assistantReply));
        }
        intake.setMessagesJson(serialise(messages));
        intake.setLastClientTurnId(clientTurnId);
        intake.setLastAssistantReply(assistantReply);
        intakeRepository.save(intake);
        return true;
    }

    /**
     * Answer a duplicate submit of the most recently processed turn from the
     * server's own record. Returns the stored reply, or null when there is
     * nothing stored for this turn id.
     */
    @Transactional(readOnly = true)
    public String storedReplyForTurn(Long intakeId, Long patientId, String clientTurnId) {
        if (clientTurnId == null || clientTurnId.isBlank()) return null;
        KioskIntake intake = requireOwnedIntake(intakeId, patientId);
        if (clientTurnId.equals(intake.getLastClientTurnId())) {
            return intake.getLastAssistantReply();
        }
        return null;
    }

    /**
     * True when the given client turn id matches the server's record of the
     * most recently processed turn — used to avoid re-invoking the provider.
     */
    @Transactional(readOnly = true)
    public boolean isKnownTurn(Long intakeId, Long patientId, String clientTurnId) {
        if (clientTurnId == null || clientTurnId.isBlank()) return false;
        KioskIntake intake = requireOwnedIntake(intakeId, patientId);
        return clientTurnId.equals(intake.getLastClientTurnId());
    }

    /**
     * "Start over" for the conversational intake: clears the server-side
     * conversation history (and the remembered turn id) so the patient can run
     * the interview again from scratch. Only allowed while the intake is still
     * IN_PROGRESS and consent has been granted; any previously persisted draft
     * is intentionally left untouched so it cannot be destroyed by mistake.
     */
    @Transactional
    public KioskIntake resetConversation(Long intakeId, Long patientId) {
        KioskIntake intake = requireOwnedIntake(intakeId, patientId);
        if (intake.getStatus() != KioskIntakeStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Only an in-progress intake can be reset (current: " + intake.getStatus() + ").");
        }
        if (intake.getConsent() == null) {
            throw new IllegalStateException("Consent must be granted before starting the intake.");
        }
        intake.setMessagesJson("[]");
        intake.setLastClientTurnId(null);
        intake.setLastAssistantReply(null);
        return intakeRepository.save(intake);
    }

    /**
     * Read the stored conversation history as message entries, for rendering
     * the conversation on the server-rendered intake page (refresh-safe).
     */
    @Transactional(readOnly = true)
    public List<MessageEntry> getConversationMessages(Long intakeId, Long patientId) {
        KioskIntake intake = requireOwnedIntake(intakeId, patientId);
        return deserialiseMessages(intake.getMessagesJson());
    }

    @Transactional(readOnly = true)
    public List<AiChatRequest.Message> getConversationHistory(Long intakeId, Long patientId) {
        KioskIntake intake = requireOwnedIntake(intakeId, patientId);
        List<AiChatRequest.Message> result = new ArrayList<>();
        for (MessageEntry e : deserialiseMessages(intake.getMessagesJson())) {
            result.add(new AiChatRequest.Message(e.getRole(), e.getContent()));
        }
        return result;
    }

    // ---- Draft persistence ----

    @Transactional
    public AiDraftDto saveDraft(Long intakeId, Long patientId, String structuredJson) {
        return saveDraft(intakeId, patientId, structuredJson, false);
    }

    @Transactional
    public AiDraftDto saveDraft(Long intakeId, Long patientId, String structuredJson, boolean urgent) {
        KioskIntake intake = requireOwnedIntake(intakeId, patientId);

        AiDraftDto clean = validator.parseAndValidate(structuredJson);
        String cleanJson = validator.serialise(clean);

        intake.setDraftJson(cleanJson);
        intake.setRedFlagsJson(serialise(clean.getRedFlags()));
        intake.setStatus(KioskIntakeStatus.DRAFT_READY);
        intakeRepository.save(intake);

        persistRedFlags(intake, clean.getRedFlags(), urgent);

        auditService.log(AuditAction.AI_DRAFT_GENERATED, "KioskIntake", intakeId,
                "Kiosk intake draft generated");
        log.info("Kiosk intake {} draft saved ({} symptom(s))", intakeId,
                clean.getSymptoms() != null ? clean.getSymptoms().size() : 0);
        return clean;
    }

    @Transactional(readOnly = true)
    public AiDraftDto getDraft(Long intakeId, Long patientId) {
        KioskIntake intake = requireOwnedIntake(intakeId, patientId);
        return validator.deserialise(intake.getDraftJson());
    }

    @Transactional(readOnly = true)
    public AiDraftDto getDraftForReview(Long intakeId) {
        return validator.deserialise(intakeRepository.findById(intakeId)
                .map(KioskIntake::getDraftJson)
                .orElse(null));
    }

    /**
     * Persist a draft built from the guided, structured kiosk questionnaire.
     *
     * Alternative workflow used when the AI assistant is disabled/unavailable:
     * the patient answers guided sections (chief complaint, HPI, background,
     * safety signals) and the answers are assembled into the SAME validated
     * AiDraftDto that the AI conversation produces, keeping the downstream
     * review/accept pipeline identical.
     *
     * @return the intake now in DRAFT_READY state
     */
    @Transactional
    public KioskIntake saveGuidedDraft(Long intakeId, Long patientId, GuidedIntakeForm form) {
        KioskIntake intake = requireOwnedIntake(intakeId, patientId);
        if (intake.getConsent() == null) {
            throw new IllegalStateException("Patient consent must be granted before submitting an intake.");
        }
        AiDraftDto draft = buildGuidedDraft(form);
        String structuredJson = validator.serialise(draft);
        boolean urgent = form != null && form.hasUrgentSignals();
        saveDraft(intakeId, patientId, structuredJson, urgent);
        return intake;
    }

    /**
     * Assemble a patient-reported {@link AiDraftDto} from the guided questionnaire
     * answers. Only what the patient typed/selected is included — nothing is
     * inferred or invented. Narrative fields are concatenated from the guided
     * sub-fields so the clinician review page reads a coherent history.
     */
    AiDraftDto buildGuidedDraft(GuidedIntakeForm form) {
        AiDraftDto draft = new AiDraftDto();
        if (form == null) {
            return draft;
        }
        draft.setChiefComplaint(form.getChiefComplaint());

        List<String> hpiParts = new ArrayList<>();
        if (notBlank(form.getSymptomOnset())) {
            hpiParts.add("Onset: " + form.getSymptomOnset().toLowerCase() + " onset.");
        }
        if (notBlank(form.getSymptomDuration())) {
            hpiParts.add("Duration: " + form.getSymptomDuration() + ".");
        }
        if (notBlank(form.getHpi())) {
            hpiParts.add(form.getHpi());
        }
        if (notBlank(form.getAggravating())) {
            hpiParts.add("Makes it worse: " + form.getAggravating() + ".");
        }
        if (notBlank(form.getRelieving())) {
            hpiParts.add("Relieves it: " + form.getRelieving() + ".");
        }
        if (notBlank(form.getAdditionalNotes())) {
            hpiParts.add("Additional: " + form.getAdditionalNotes() + ".");
        }
        draft.setHistoryOfPresentIllness(String.join(" ", hpiParts));

        List<String> historyParts = new ArrayList<>();
        if (notBlank(form.getPastMedicalHistory())) {
            historyParts.add("Past medical history: " + form.getPastMedicalHistory() + ".");
        }
        if (notBlank(form.getCurrentMedications())) {
            historyParts.add("Current medications: " + form.getCurrentMedications() + ".");
        }
        if (notBlank(form.getAllergies())) {
            historyParts.add("Allergies: " + form.getAllergies() + ".");
        }
        if (notBlank(form.getFamilyHistory())) {
            historyParts.add("Family history: " + form.getFamilyHistory() + ".");
        }
        if (notBlank(form.getHabits())) {
            historyParts.add("Lifestyle: " + form.getHabits() + ".");
        }
        draft.setRelevantHistory(String.join(" ", historyParts));

        List<AiSymptomDraft> symptoms = new ArrayList<>();
        if (notBlank(form.getChiefComplaint())) {
            AiSymptomDraft primary = new AiSymptomDraft();
            primary.setName(form.getChiefComplaint());
            primary.setDuration(form.getSymptomDuration());
            primary.setSeverity(form.getSymptomSeverity());
            primary.setOnset(form.getSymptomOnset());
            primary.setNotes(notBlank(form.getComplaintDetail()) ? form.getComplaintDetail() : null);
            primary.setConfidence(DraftFieldConfidence.PATIENT_REPORTED);
            symptoms.add(primary);
        }
        if (form.getAssociatedSymptoms() != null) {
            for (String name : form.getAssociatedSymptoms()) {
                if (notBlank(name)) {
                    AiSymptomDraft assoc = new AiSymptomDraft();
                    assoc.setName(name);
                    assoc.setConfidence(DraftFieldConfidence.PATIENT_REPORTED);
                    symptoms.add(assoc);
                }
            }
        }
        draft.setSymptoms(symptoms);
        draft.setRedFlags(form.getSafetySignals() != null ? new ArrayList<>(form.getSafetySignals()) : new ArrayList<>());
        return draft;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    // ---- AYUSH ----

    @Transactional
    public AyushAssessment saveAyush(Long intakeId, Long patientId, AyushForm form) {
        KioskIntake intake = requireOwnedIntake(intakeId, patientId);

        AyushAssessment assessment = ayushRepository.findByIntakeId(intakeId).orElse(new AyushAssessment());
        assessment.setIntake(intake);
        copyAyush(form, assessment);
        AyushAssessment saved = ayushRepository.save(assessment);

        intake.setAyushJson(serialise(saved));
        intakeRepository.save(intake);
        return saved;
    }

    @Transactional(readOnly = true)
    public AyushAssessment getAyush(Long intakeId) {
        return ayushRepository.findByIntakeId(intakeId).orElse(null);
    }

    // ---- Submit for clinician review ----

    @Transactional
    public KioskIntake submit(Long intakeId, Long patientId) {
        KioskIntake intake = requireOwnedIntake(intakeId, patientId);
        if (intake.getStatus() != KioskIntakeStatus.DRAFT_READY
                && intake.getStatus() != KioskIntakeStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Intake cannot be submitted from status " + intake.getStatus() + ".");
        }
        if (intake.getConsent() == null) {
            throw new IllegalStateException("Patient consent must be granted before submitting an intake.");
        }
        intake.setStatus(KioskIntakeStatus.SUBMITTED);
        intakeRepository.save(intake);
        auditService.log(AuditAction.INTAKE_SUBMITTED, "KioskIntake", intakeId, null);
        log.info("Kiosk intake {} submitted for clinician review", intakeId);
        return intake;
    }

    // ---- Clinician review ----

    @Transactional(readOnly = true)
    public List<KioskIntake> findSubmissionsForReview() {
        return intakeRepository.findByStatusIn(List.of(KioskIntakeStatus.SUBMITTED));
    }

    @Transactional(readOnly = true)
    public long countPendingReview() {
        return intakeRepository.countByStatus(KioskIntakeStatus.SUBMITTED);
    }

    @Transactional(readOnly = true)
    public KioskIntake requireIntakeForReview(Long intakeId) {
        return intakeRepository.findById(intakeId)
                .orElseThrow(() -> new ResourceNotFoundException("Intake not found: " + intakeId));
    }

    @Transactional(readOnly = true)
    public List<RedFlag> getRedFlagsForIntake(Long intakeId) {
        return redFlagRepository.findByIntakeId(intakeId);
    }

    @Transactional(readOnly = true)
    public List<Document> getDocumentsForIntake(Long intakeId) {
        KioskIntake intake = requireIntakeForReview(intakeId);
        return documentRepository.findByPatientIdOrderByUploadedAtDesc(
                intake.getPatient().getId());
    }

    /**
     * Accept the intake: creates a real PatientCase + Encounter, applies only
     * patient-reported fields from the validated draft, links uploaded documents,
     * and transfers red flags + AYUSH into the clinical record.
     */
    @Transactional
    public Encounter acceptIntake(Long intakeId, String clinicianUsername, String notes) {
        KioskIntake intake = requireIntakeForReview(intakeId);
        if (intake.getStatus() != KioskIntakeStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Only submitted intakes can be accepted (current: " + intake.getStatus() + ").");
        }

        AiDraftDto draft = validator.deserialise(intake.getDraftJson());
        if (draft == null) {
            throw new IllegalStateException("Intake has no valid draft to accept.");
        }

        User clinician = userRepository.findByUsername(clinicianUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Clinician not found: " + clinicianUsername));

        String chiefComplaint = draft.getChiefComplaint() != null && !draft.getChiefComplaint().isBlank()
                ? draft.getChiefComplaint() : "Patient self-service intake";
        String title = chiefComplaint.length() > 240 ? chiefComplaint.substring(0, 240) : chiefComplaint;

        PatientCase patientCase = new PatientCase();
        patientCase.setCaseNumber(generateCaseNumber());
        patientCase.setPatient(intake.getPatient());
        patientCase.setTitle(title);
        patientCase.setChiefComplaint(chiefComplaint);
        patientCase.setStatus(CaseStatus.IN_PROGRESS);
        patientCase.setPriority(determinePriority(intakeId));
        PatientCase savedCase = caseRepository.save(patientCase);

        Encounter encounter = new Encounter();
        encounter.setPatientCase(savedCase);
        encounter.setClinician(clinician);
        encounter.setStatus(EncounterStatus.DRAFT);
        encounter.setChiefComplaint(draft.getChiefComplaint());
        encounter.setHistoryOfPresentIllness(draft.getHistoryOfPresentIllness());
        encounter.setRelevantHistory(draft.getRelevantHistory());
        Encounter savedEncounter = encounterRepository.save(encounter);

        applySymptoms(savedEncounter, draft.getSymptoms());

        for (RedFlag flag : redFlagRepository.findByIntakeId(intakeId)) {
            flag.setIntake(null);
            flag.setEncounter(savedEncounter);
            flag.setPatient(intake.getPatient());
            redFlagRepository.save(flag);
        }

        ayushRepository.findByIntakeId(intakeId).ifPresent(a -> {
            a.setIntake(null);
            a.setEncounter(savedEncounter);
            ayushRepository.save(a);
        });

        linkDocumentsToEncounter(savedCase, savedEncounter);

        intake.setStatus(KioskIntakeStatus.ACCEPTED);
        intake.setReviewedBy(clinicianUsername);
        intake.setReviewedAt(LocalDateTime.now());
        intake.setClinicianNotes(notes);
        intakeRepository.save(intake);

        auditService.log(AuditAction.INTAKE_ACCEPTED, "KioskIntake", intakeId,
                "Intake accepted into case " + savedCase.getCaseNumber());
        return savedEncounter;
    }

    @Transactional
    public void rejectIntake(Long intakeId, String clinicianUsername, String notes) {
        KioskIntake intake = requireIntakeForReview(intakeId);
        if (intake.getStatus() != KioskIntakeStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Only submitted intakes can be rejected (current: " + intake.getStatus() + ").");
        }
        intake.setStatus(KioskIntakeStatus.REJECTED);
        intake.setReviewedBy(clinicianUsername);
        intake.setReviewedAt(LocalDateTime.now());
        intake.setClinicianNotes(notes);
        intakeRepository.save(intake);
        auditService.log(AuditAction.INTAKE_REJECTED, "KioskIntake", intakeId, "Intake rejected");
        log.info("Kiosk intake {} rejected by {}", intakeId, clinicianUsername);
    }

    // ---- Helpers ----

    private CasePriority determinePriority(Long intakeId) {
        List<RedFlag> flags = redFlagRepository.findByIntakeId(intakeId);
        if (flags.stream().anyMatch(RedFlag::isUrgent)) return CasePriority.URGENT;
        if (!flags.isEmpty()) return CasePriority.HIGH;
        return CasePriority.MEDIUM;
    }

    private void applySymptoms(Encounter encounter, List<AiSymptomDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) return;
        for (AiSymptomDraft d : drafts) {
            if (d.getName() == null || d.getName().isBlank()) continue;
            Symptom symptom = new Symptom();
            symptom.setEncounter(encounter);
            symptom.setName(d.getName());
            symptom.setDuration(d.getDuration());
            symptom.setSeverity(parseSeverity(d.getSeverity()));
            symptom.setOnset(parseOnset(d.getOnset()));
            symptom.setNotes(d.getNotes());
            symptomRepository.save(symptom);
        }
    }

    private Severity parseSeverity(String raw) {
        if (raw == null || raw.isBlank()) return Severity.MILD;
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

    private void linkDocumentsToEncounter(PatientCase patientCase, Encounter encounter) {
        for (Document doc : documentRepository.findByPatientIdOrderByUploadedAtDesc(
                patientCase.getPatient().getId())) {
            if (doc.getPatientCase() == null && doc.getEncounter() == null) {
                doc.setPatientCase(patientCase);
                doc.setEncounter(encounter);
                documentRepository.save(doc);
            }
        }
    }

    private void persistRedFlags(KioskIntake intake, List<String> flags, boolean urgent) {
        for (String description : flags) {
            RedFlag flag = new RedFlag();
            flag.setIntake(intake);
            flag.setPatient(intake.getPatient());
            flag.setDescription(description);
            flag.setUrgent(urgent);
            flag.setSource(RedFlagSource.AI_INTELLIGENCE);
            redFlagRepository.save(flag);
        }
        if (urgent) {
            auditService.log(AuditAction.RED_FLAG_IDENTIFIED, "KioskIntake", intake.getId(),
                    "Potentially urgent patient-safety observation(s) recorded");
        } else if (!flags.isEmpty()) {
            auditService.log(AuditAction.RED_FLAG_IDENTIFIED, "KioskIntake", intake.getId(),
                    flags.size() + " patient-safety observation(s) recorded");
        }
    }

    private void copyAyush(AyushForm form, AyushAssessment assessment) {
        assessment.setPrakriti(form.getPrakriti());
        assessment.setVikriti(form.getVikriti());
        assessment.setSara(form.getSara());
        assessment.setSamhanana(form.getSamhanana());
        assessment.setPramana(form.getPramana());
        assessment.setSatmya(form.getSatmya());
        assessment.setSatva(form.getSatva());
        assessment.setAharaShakti(form.getAharaShakti());
        assessment.setVyayamaShakti(form.getVyayamaShakti());
        assessment.setVaya(form.getVaya());
        assessment.setAharaDetails(form.getAharaDetails());
        assessment.setViharaDetails(form.getViharaDetails());
        assessment.setNotes(form.getNotes());
    }

    private String generateCaseNumber() {
        Long nextVal = jdbcTemplate.queryForObject("SELECT NEXTVAL('case_number_seq')", Long.class);
        int year = java.time.LocalDate.now().getYear();
        return "C-" + year + "-" + String.format("%03d", nextVal);
    }

    // ---- JSON helpers ----

    private List<MessageEntry> deserialiseMessages(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<MessageEntry>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialise kiosk messages: {}", e.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }

    private String serialise(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** AYUSH intake form (Dashavidha Pariksha + Ahara-Vihara). */
    public static class AyushForm {
        private String prakriti;
        private String vikriti;
        private String sara;
        private String samhanana;
        private String pramana;
        private String satmya;
        private String satva;
        private String aharaShakti;
        private String vyayamaShakti;
        private String vaya;
        private String aharaDetails;
        private String viharaDetails;
        private String notes;

        public String getPrakriti() { return prakriti; }
        public void setPrakriti(String prakriti) { this.prakriti = prakriti; }
        public String getVikriti() { return vikriti; }
        public void setVikriti(String vikriti) { this.vikriti = vikriti; }
        public String getSara() { return sara; }
        public void setSara(String sara) { this.sara = sara; }
        public String getSamhanana() { return samhanana; }
        public void setSamhanana(String samhanana) { this.samhanana = samhanana; }
        public String getPramana() { return pramana; }
        public void setPramana(String pramana) { this.pramana = pramana; }
        public String getSatmya() { return satmya; }
        public void setSatmya(String satmya) { this.satmya = satmya; }
        public String getSatva() { return satva; }
        public void setSatva(String satva) { this.satva = satva; }
        public String getAharaShakti() { return aharaShakti; }
        public void setAharaShakti(String aharaShakti) { this.aharaShakti = aharaShakti; }
        public String getVyayamaShakti() { return vyayamaShakti; }
        public void setVyayamaShakti(String vyayamaShakti) { this.vyayamaShakti = vyayamaShakti; }
        public String getVaya() { return vaya; }
        public void setVaya(String vaya) { this.vaya = vaya; }
        public String getAharaDetails() { return aharaDetails; }
        public void setAharaDetails(String aharaDetails) { this.aharaDetails = aharaDetails; }
        public String getViharaDetails() { return viharaDetails; }
        public void setViharaDetails(String viharaDetails) { this.viharaDetails = viharaDetails; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    /** Internal DTO for message JSON serialisation. */
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

    /**
     * Guided, structured kiosk questionnaire — the alternative intake workflow
     * that works without an AI provider. All fields are patient-reported.
     *
     * "safetySignals" carries neutral patient-safety observations the patient
     * explicitly selected (red flags for the clinician). "hasUrgentSignals"
     * marks potentially-urgent combinations for triage, matching the AI flow's
     * urgentFlag semantics.
     */
    public static class GuidedIntakeForm {
        private String chiefComplaint;
        private String complaintDetail;
        private String symptomDuration;
        private String symptomOnset;
        private String symptomSeverity;
        private String hpi;
        private String aggravating;
        private String relieving;
        private List<String> associatedSymptoms = new ArrayList<>();
        private String pastMedicalHistory;
        private String currentMedications;
        private String allergies;
        private String familyHistory;
        private String habits;
        private List<String> safetySignals = new ArrayList<>();
        private String additionalNotes;

        private static final java.util.Set<String> URGENT_SIGNALS = java.util.Set.of(
                "Chest pain or pressure",
                "Difficulty breathing",
                "Severe bleeding",
                "Fainting or loss of consciousness",
                "Sudden weakness or numbness on one side",
                "Sudden, severe headache (worst ever)");

        public boolean hasUrgentSignals() {
            if (safetySignals == null) return false;
            for (String signal : safetySignals) {
                if (URGENT_SIGNALS.contains(signal)) return true;
            }
            return false;
        }

        public String getChiefComplaint() { return chiefComplaint; }
        public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }
        public String getComplaintDetail() { return complaintDetail; }
        public void setComplaintDetail(String complaintDetail) { this.complaintDetail = complaintDetail; }
        public String getSymptomDuration() { return symptomDuration; }
        public void setSymptomDuration(String symptomDuration) { this.symptomDuration = symptomDuration; }
        public String getSymptomOnset() { return symptomOnset; }
        public void setSymptomOnset(String symptomOnset) { this.symptomOnset = symptomOnset; }
        public String getSymptomSeverity() { return symptomSeverity; }
        public void setSymptomSeverity(String symptomSeverity) { this.symptomSeverity = symptomSeverity; }
        public String getHpi() { return hpi; }
        public void setHpi(String hpi) { this.hpi = hpi; }
        public String getAggravating() { return aggravating; }
        public void setAggravating(String aggravating) { this.aggravating = aggravating; }
        public String getRelieving() { return relieving; }
        public void setRelieving(String relieving) { this.relieving = relieving; }
        public List<String> getAssociatedSymptoms() { return associatedSymptoms; }
        public void setAssociatedSymptoms(List<String> associatedSymptoms) {
            this.associatedSymptoms = associatedSymptoms != null ? associatedSymptoms : new ArrayList<>();
        }
        public String getPastMedicalHistory() { return pastMedicalHistory; }
        public void setPastMedicalHistory(String pastMedicalHistory) { this.pastMedicalHistory = pastMedicalHistory; }
        public String getCurrentMedications() { return currentMedications; }
        public void setCurrentMedications(String currentMedications) { this.currentMedications = currentMedications; }
        public String getAllergies() { return allergies; }
        public void setAllergies(String allergies) { this.allergies = allergies; }
        public String getFamilyHistory() { return familyHistory; }
        public void setFamilyHistory(String familyHistory) { this.familyHistory = familyHistory; }
        public String getHabits() { return habits; }
        public void setHabits(String habits) { this.habits = habits; }
        public List<String> getSafetySignals() { return safetySignals; }
        public void setSafetySignals(List<String> safetySignals) {
            this.safetySignals = safetySignals != null ? safetySignals : new ArrayList<>();
        }
        public String getAdditionalNotes() { return additionalNotes; }
        public void setAdditionalNotes(String additionalNotes) { this.additionalNotes = additionalNotes; }
    }
}