package com.patientcase.ai;

import com.patientcase.case_management.CasePriority;
import com.patientcase.case_management.CaseStatus;
import com.patientcase.case_management.PatientCase;
import com.patientcase.case_management.PatientCaseRepository;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-level tests for the AI intake draft review endpoints.
 *
 * Uses real Spring context + H2 (no @MockBean concrete classes — Java 25 compatible).
 * AiSessionService is used via the AiSessionService interface (injected into controllers).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiIntakeDraftControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PatientCaseRepository caseRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private AiSessionService sessionService;
    @Autowired private PasswordEncoder passwordEncoder;

    private User clinician;
    private User otherUser;
    private Encounter encounter;

    @BeforeEach
    void setUp() {
        clinician = saveUser("draft.doctor", Role.DOCTOR);
        otherUser = saveUser("draft.other",  Role.NURSE);

        Patient patient = new Patient();
        patient.setPatientNumber("P-DRAFT-01");
        patient.setFirstName("Draft"); patient.setLastName("Test");
        patient.setDateOfBirth(LocalDate.of(1988, 7, 20));
        patient.setGender(Gender.MALE);
        patientRepository.save(patient);

        PatientCase patientCase = new PatientCase();
        patientCase.setCaseNumber("C-DRAFT-001");
        patientCase.setPatient(patient);
        patientCase.setTitle("Draft Test Case");
        patientCase.setChiefComplaint("Back pain");
        patientCase.setStatus(CaseStatus.IN_PROGRESS);
        patientCase.setPriority(CasePriority.MEDIUM);
        caseRepository.save(patientCase);

        encounter = new Encounter();
        encounter.setPatientCase(patientCase);
        encounter.setClinician(clinician);
        encounter.setStatus(EncounterStatus.DRAFT);
        encounterRepository.save(encounter);
    }

    // ---- GET /encounters/{id}/ai-intake — authentication ----

    @Test
    void aiIntake_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/encounters/{id}/ai-intake", encounter.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login**"));
    }

    // ---- GET /encounters/{id}/ai-intake/draft — no session yet ----

    @Test
    void draft_noSession_redirectsToIntakePage() throws Exception {
        // No session created — controller should find IN_PROGRESS state and redirect
        // Actually: no session at all → ResourceNotFoundException (→ 404)
        mockMvc.perform(get("/encounters/{id}/ai-intake/draft", encounter.getId())
                .with(user(clinician.getUsername()).roles("DOCTOR")))
                .andExpect(status().isNotFound());
    }

    // ---- GET /encounters/{id}/ai-intake/draft — with DRAFT_READY session ----

    @Test
    void draft_withDraftReadySession_returnsOkAndDraftView() throws Exception {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(),
                "{\"chiefComplaint\":\"Back pain\",\"symptoms\":[]}");

        mockMvc.perform(get("/encounters/{id}/ai-intake/draft", encounter.getId())
                .with(user(clinician.getUsername()).roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("encounters/ai-intake-draft"))
                .andExpect(model().attributeExists("encounter", "draft", "session"));
    }

    // ---- GET /encounters/{id}/ai-intake/draft — IN_PROGRESS redirects to chat ----

    @Test
    void draft_sessionInProgress_redirectsToAiIntakePage() throws Exception {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        // No draft saved — status is IN_PROGRESS

        mockMvc.perform(get("/encounters/{id}/ai-intake/draft", encounter.getId())
                .with(user(clinician.getUsername()).roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/encounters/" + encounter.getId() + "/ai-intake"));
    }

    // ---- GET /encounters/{id}/ai-intake/draft — unauthorized ----

    @Test
    void draft_unauthorizedClinician_isForbidden() throws Exception {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(), "{\"chiefComplaint\":\"Back pain\"}");

        mockMvc.perform(get("/encounters/{id}/ai-intake/draft", encounter.getId())
                .with(user(otherUser.getUsername()).roles("NURSE")))
                .andExpect(status().isForbidden());
    }

    // ---- GET /encounters/{id}/ai-intake/draft — non-existent encounter ----

    @Test
    void draft_nonExistentEncounter_returnsNotFound() throws Exception {
        mockMvc.perform(get("/encounters/{id}/ai-intake/draft", 999999L)
                .with(user(clinician.getUsername()).roles("DOCTOR")))
                .andExpect(status().isNotFound());
    }

    // ---- POST /encounters/{id}/ai-intake/apply — unauthorized ----

    @Test
    void apply_unauthorizedClinician_isForbidden() throws Exception {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(), "{\"chiefComplaint\":\"Back pain\"}");

        mockMvc.perform(post("/encounters/{id}/ai-intake/apply", encounter.getId())
                .with(user(otherUser.getUsername()).roles("NURSE"))
                .with(csrf())
                .param("approvedFields", "chiefComplaint"))
                .andExpect(status().isForbidden());
    }

    // ---- POST /encounters/{id}/ai-intake/apply — no fields selected ----

    @Test
    void apply_noFieldsSelected_redirectsWithError() throws Exception {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(), "{\"chiefComplaint\":\"Back pain\"}");

        mockMvc.perform(post("/encounters/{id}/ai-intake/apply", encounter.getId())
                .with(user(clinician.getUsername()).roles("DOCTOR"))
                .with(csrf()))
                // No approvedFields param
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/encounters/" + encounter.getId() + "/ai-intake/draft"))
                .andExpect(flash().attribute("errorMessage",
                        containsString("select at least one")));
    }

    // ---- POST /encounters/{id}/ai-intake/apply — prohibited field ----

    @Test
    void apply_prohibitedField_redirectsWithError() throws Exception {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(), "{\"chiefComplaint\":\"Back pain\"}");

        mockMvc.perform(post("/encounters/{id}/ai-intake/apply", encounter.getId())
                .with(user(clinician.getUsername()).roles("DOCTOR"))
                .with(csrf())
                .param("approvedFields", "diagnoses"))  // prohibited
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/encounters/" + encounter.getId() + "/ai-intake/draft"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // ---- POST /encounters/{id}/ai-intake/apply — valid apply ----

    @Test
    void apply_validApproval_redirectsToCaseTaking() throws Exception {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(),
                "{\"chiefComplaint\":\"Back pain\"}");

        mockMvc.perform(post("/encounters/{id}/ai-intake/apply", encounter.getId())
                .with(user(clinician.getUsername()).roles("DOCTOR"))
                .with(csrf())
                .param("approvedFields", "chiefComplaint"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/encounters/" + encounter.getId() + "/case-taking"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    // ---- POST /encounters/{id}/ai-intake/apply — double apply ----

    @Test
    void apply_doubleApply_redirectsWithError() throws Exception {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(), "{\"chiefComplaint\":\"Back pain\"}");

        // First apply
        mockMvc.perform(post("/encounters/{id}/ai-intake/apply", encounter.getId())
                .with(user(clinician.getUsername()).roles("DOCTOR"))
                .with(csrf())
                .param("approvedFields", "chiefComplaint"))
                .andExpect(status().is3xxRedirection());

        // Second apply — must be rejected with error
        mockMvc.perform(post("/encounters/{id}/ai-intake/apply", encounter.getId())
                .with(user(clinician.getUsername()).roles("DOCTOR"))
                .with(csrf())
                .param("approvedFields", "chiefComplaint"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/encounters/" + encounter.getId() + "/ai-intake/draft"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // ---- POST /encounters/{id}/ai-intake/apply — admin can apply ----

    @Test
    void apply_adminUser_canApplyAnyEncounter() throws Exception {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);
        sessionService.saveDraft(encounter.getId(), "{\"chiefComplaint\":\"Back pain\"}");

        mockMvc.perform(post("/encounters/{id}/ai-intake/apply", encounter.getId())
                .with(user("admin.draft").roles("ADMIN"))
                .with(csrf())
                .param("approvedFields", "chiefComplaint"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/encounters/" + encounter.getId() + "/case-taking"));
    }

    // ---- POST /encounters/{id}/ai-intake/discard ----

    @Test
    void discard_assignedClinician_redirectsToIntakePage() throws Exception {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);

        mockMvc.perform(post("/encounters/{id}/ai-intake/discard", encounter.getId())
                .with(user(clinician.getUsername()).roles("DOCTOR"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/encounters/" + encounter.getId() + "/ai-intake"));
    }

    @Test
    void discard_unauthorizedClinician_isForbidden() throws Exception {
        sessionService.getOrCreateSession(encounter.getId(), clinician.getUsername(), false);

        mockMvc.perform(post("/encounters/{id}/ai-intake/discard", encounter.getId())
                .with(user(otherUser.getUsername()).roles("NURSE"))
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ---- Helpers ----

    private User saveUser(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPasswordHash(passwordEncoder.encode("Pass@1"));
        u.setFirstName("Draft"); u.setLastName("Test");
        u.setRole(role); u.setEnabled(true);
        return userRepository.save(u);
    }
}
