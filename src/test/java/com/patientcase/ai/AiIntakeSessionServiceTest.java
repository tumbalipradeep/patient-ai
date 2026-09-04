package com.patientcase.ai;

import com.patientcase.case_management.CasePriority;
import com.patientcase.case_management.CaseStatus;
import com.patientcase.case_management.PatientCase;
import com.patientcase.case_management.PatientCaseRepository;
import com.patientcase.clinical.Severity;
import com.patientcase.clinical.SymptomRepository;
import com.patientcase.encounter.Encounter;
import com.patientcase.encounter.EncounterRepository;
import com.patientcase.encounter.EncounterStatus;
import com.patientcase.patient.Gender;
import com.patientcase.patient.Patient;
import com.patientcase.patient.PatientRepository;
import com.patientcase.user.Role;
import com.patientcase.user.User;
import com.patientcase.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for AiIntakeSessionService.
 * Uses real H2 DB with ddl-auto=create-drop via the test profile.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AiIntakeSessionServiceTest {

    @Autowired private AiSessionService sessionService;
    @Autowired private AiIntakeSessionRepository sessionRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private PatientCaseRepository caseRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User clinician;
    private User otherUser;
    private Encounter encounter;

    @BeforeEach
    void setUp() {
        clinician = saveUser("svc.doctor", Role.DOCTOR);
        otherUser = saveUser("svc.other",  Role.NURSE);

        Patient patient = new Patient();
        patient.setPatientNumber("P-SVC-01");
        patient.setFirstName("Svc"); patient.setLastName("Test");
        patient.setDateOfBirth(LocalDate.of(1990, 3, 15));
        patient.setGender(Gender.FEMALE);
        patientRepository.save(patient);

        PatientCase patientCase = new PatientCase();
        patientCase.setCaseNumber("C-SVC-001");
        patientCase.setPatient(patient);
        patientCase.setTitle("Session Service Test");
        patientCase.setChiefComplaint("Cough");
        patientCase.setStatus(CaseStatus.IN_PROGRESS);
        patientCase.setPriority(CasePriority.LOW);
        caseRepository.save(patientCase);

        encounter = new Encounter();
        encounter.setPatientCase(patientCase);
        encounter.setClinician(clinician);
        encounter.setStatus(EncounterStatus.DRAFT);
        encounterRepository.save(encounter);
    }

    // ---- Session creation ----

    @Test
    void getOrCreateSession_newEncounter_createsInProgressSession() {
        AiIntakeSession session = sessionService.getOrCreateSession(
                encounter.getId(), clinician.getUsername(), false);

        assertThat(session).isNotNull();
        assertThat(session.getStatus()).isEqualTo(AiIntakeSessionStatus.IN_PROGRESS);
        assertThat(session.getCreatedBy()).isEqualTo(clinician.getUsername());
        assertThat(session.getEncounter().getId()).isEqualTo(encounter.getId());
    }

    @Test
    void getOrCreateSession_calledTwice_returnsSameSession() {
        AiIntakeSession first  = sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        AiIntakeSession second = sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        assertThat(first.getId()).isEqualTo(second.getId());
    }

    @Test
    void getOrCreateSession_afterDiscard_createsNewSession() {
        AiIntakeSession original = sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        Long originalId = original.getId();

        sessionService.discardSession(encounter.getId(), clinician.getUsername(), false);

        AiIntakeSession fresh = sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        assertThat(fresh.getId()).isNotEqualTo(originalId);
        assertThat(fresh.getStatus()).isEqualTo(AiIntakeSessionStatus.IN_PROGRESS);
    }    // ---- Message persistence ----

    @Test
    void appendMessages_storesBothTurns() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.appendMessages(encounter.getId(), "Hello", "Hi, how can I help?",
                clinician.getUsername(), false);

        var history = sessionService.getConversationHistory(encounter.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getRole()).isEqualTo("user");
        assertThat(history.get(0).getContent()).isEqualTo("Hello");
        assertThat(history.get(1).getRole()).isEqualTo("assistant");
        assertThat(history.get(1).getContent()).isEqualTo("Hi, how can I help?");
    }

    @Test
    void getConversationHistory_noSession_returnsEmptyList() {
        var history = sessionService.getConversationHistory(encounter.getId());
        assertThat(history).isEmpty();
    }

    // ---- Draft persistence ----

    @Test
    void saveDraft_validDraft_transitionsToDraftReady() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);

        String json = """
            {"chiefComplaint":"Cough","symptoms":[
              {"name":"Cough","severity":"MODERATE","onset":"GRADUAL"}
            ]}
            """;
        AiDraftDto draft = sessionService.saveDraft(encounter.getId(), json);

        assertThat(draft.getChiefComplaint()).isEqualTo("Cough");

        AiIntakeSession session = sessionRepository.findByEncounterId(encounter.getId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(AiIntakeSessionStatus.DRAFT_READY);
        assertThat(session.getDraftJson()).isNotBlank();
    }

    @Test
    void saveDraft_withDiagnoses_throwsValidationException() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);

        String json = """
            {"chiefComplaint":"Fever","diagnoses":["Malaria"]}
            """;
        assertThatThrownBy(() -> sessionService.saveDraft(encounter.getId(), json))
                .isInstanceOf(AiDraftValidator.AiDraftValidationException.class)
                .hasMessageContaining("diagnoses");
    }

    @Test
    void getDraft_noDraftYet_returnsNull() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        assertThat(sessionService.getDraft(encounter.getId())).isNull();
    }

    // ---- Apply draft ----

    @Test
    void applyDraft_approvedChiefComplaint_writesEncounterField() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(),
                "{\"chiefComplaint\":\"Severe headache\"}");

        sessionService.applyDraft(encounter.getId(),
                Set.of("chiefComplaint"),
                clinician.getUsername(), false);

        Encounter updated = encounterRepository.findById(encounter.getId()).orElseThrow();
        assertThat(updated.getChiefComplaint()).isEqualTo("Severe headache");
    }

    @Test
    void applyDraft_approvedSymptoms_replacesSymptoms() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        String json = """
            {"symptoms":[
              {"name":"Nausea","severity":"MILD","onset":"SUDDEN"}
            ]}
            """;
        sessionService.saveDraft(encounter.getId(), json);
        sessionService.applyDraft(encounter.getId(),
                Set.of("symptoms"),
                clinician.getUsername(), false);

        var symptoms = symptomRepository.findByEncounterId(encounter.getId());
        assertThat(symptoms).hasSize(1);
        assertThat(symptoms.get(0).getName()).isEqualTo("Nausea");
        assertThat(symptoms.get(0).getSeverity()).isEqualTo(Severity.MILD);
    }

    @Test
    void applyDraft_sessionBecomesApplied() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(), "{\"chiefComplaint\":\"Fever\"}");
        sessionService.applyDraft(encounter.getId(),
                Set.of("chiefComplaint"),
                clinician.getUsername(), false);

        AiIntakeSession session = sessionRepository.findByEncounterId(encounter.getId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(AiIntakeSessionStatus.APPLIED);
    }

    @Test
    void applyDraft_doubleApply_throwsIllegalState() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(), "{\"chiefComplaint\":\"Fever\"}");
        sessionService.applyDraft(encounter.getId(),
                Set.of("chiefComplaint"),
                clinician.getUsername(), false);

        // Second apply must be rejected
        assertThatThrownBy(() ->
            sessionService.applyDraft(encounter.getId(),
                    Set.of("chiefComplaint"),
                    clinician.getUsername(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been applied");
    }

    @Test
    void applyDraft_prohibitedField_throwsIllegalArgument() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(), "{\"chiefComplaint\":\"Fever\"}");

        assertThatThrownBy(() ->
            sessionService.applyDraft(encounter.getId(),
                    Set.of("diagnoses"),   // prohibited
                    clinician.getUsername(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diagnoses");
    }

    @Test
    void applyDraft_notDraftReady_throwsIllegalState() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        // Session is IN_PROGRESS, no draft saved

        assertThatThrownBy(() ->
            sessionService.applyDraft(encounter.getId(),
                    Set.of("chiefComplaint"),
                    clinician.getUsername(), false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void applyDraft_unauthorizedUser_throwsAccessDenied() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(), "{\"chiefComplaint\":\"Fever\"}");

        assertThatThrownBy(() ->
            sessionService.applyDraft(encounter.getId(),
                    Set.of("chiefComplaint"),
                    otherUser.getUsername(), false))  // not the assigned clinician, not admin
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void applyDraft_adminUser_canApplyAnyEncounter() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(), "{\"chiefComplaint\":\"Fever\"}");

        // Admin applies — should succeed regardless of username
        assertThatCode(() ->
            sessionService.applyDraft(encounter.getId(),
                    Set.of("chiefComplaint"),
                    "admin.user", true))
                .doesNotThrowAnyException();
    }

    // ---- Discard ----

    @Test
    void discardSession_inProgress_transitionsToDiscarded() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.discardSession(encounter.getId(), clinician.getUsername(), false);

        AiIntakeSession session = sessionRepository.findByEncounterId(encounter.getId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(AiIntakeSessionStatus.DISCARDED);
    }

    @Test
    void discardSession_applied_throwsIllegalState() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(), "{\"chiefComplaint\":\"Fever\"}");
        sessionService.applyDraft(encounter.getId(),
                Set.of("chiefComplaint"), clinician.getUsername(), false);

        assertThatThrownBy(() ->
            sessionService.discardSession(encounter.getId(), clinician.getUsername(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("applied session cannot be discarded");
    }

    // ---- Ownership checks on getOrCreateSession and appendMessages ----

    @Test
    void getOrCreateSession_unauthorizedUser_throwsAccessDenied() {
        assertThatThrownBy(() ->
            sessionService.getOrCreateSession(encounter.getId(), otherUser.getUsername(), false))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void getOrCreateSession_adminUser_canAccessAnyEncounter() {
        assertThatCode(() ->
            sessionService.getOrCreateSession(encounter.getId(), "any.admin", true))
                .doesNotThrowAnyException();
    }

    @Test
    void appendMessages_unauthorizedUser_throwsAccessDenied() {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        assertThatThrownBy(() ->
            sessionService.appendMessages(encounter.getId(), "Hello", "Hi",
                    otherUser.getUsername(), false))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // ---- Helpers ----

    private User saveUser(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPasswordHash(passwordEncoder.encode("x"));
        u.setFirstName("Svc"); u.setLastName("Test");
        u.setRole(role); u.setEnabled(true);
        return userRepository.save(u);
    }
}
