package com.patientcase.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AiController (POST /api/ai/chat).
 *
 * Mocks:
 *   - AiChatService (interface)      — avoids real HTTP calls to the AI provider
 *   - AiSessionService (interface)   — avoids concrete Byte Buddy subclassing on Java 25
 *
 * Uses a real H2 database for the encounter/user data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PatientCaseRepository caseRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // Both mocked via interfaces — no Byte Buddy subclassing, Java 25 compatible
    @MockBean private AiChatService aiService;
    @MockBean private AiSessionService sessionService;

    private User clinician;
    private User otherUser;
    private Encounter encounter;

    @BeforeEach
    void setUp() {
        clinician = saveUser("ai.clinician", Role.DOCTOR);
        otherUser = saveUser("ai.other", Role.NURSE);

        Patient patient = new Patient();
        patient.setPatientNumber("P-AI-01");
        patient.setFirstName("AI"); patient.setLastName("Test");
        patient.setDateOfBirth(LocalDate.of(1985, 1, 1));
        patient.setGender(Gender.MALE);
        patientRepository.save(patient);

        PatientCase patientCase = new PatientCase();
        patientCase.setCaseNumber("C-AI-001");
        patientCase.setPatient(patient);
        patientCase.setTitle("AI Test Case");
        patientCase.setChiefComplaint("Headache");
        patientCase.setStatus(CaseStatus.IN_PROGRESS);
        patientCase.setPriority(CasePriority.MEDIUM);
        caseRepository.save(patientCase);

        encounter = new Encounter();
        encounter.setPatientCase(patientCase);
        encounter.setClinician(clinician);
        encounter.setStatus(EncounterStatus.DRAFT);
        encounterRepository.save(encounter);

        // Default stubs for session service — session service calls must not NPE
        when(sessionService.getOrCreateSession(anyLong(), anyString(), anyBoolean()))
                .thenReturn(new AiIntakeSession());
        when(sessionService.getConversationHistory(anyLong()))
                .thenReturn(new ArrayList<>());
        when(sessionService.findSession(anyLong()))
                .thenReturn(Optional.empty());
        // saveDraft stub — returns a minimal draft (used when AI returns complete=true)
        AiDraftDto minimalDraft = new AiDraftDto();
        minimalDraft.setChiefComplaint("Headache");
        when(sessionService.saveDraft(anyLong(), anyString()))
                .thenReturn(minimalDraft);
    }

    // ---- Authentication ----

    @Test
    void chat_unauthenticated_isRedirectedToLogin() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson("Hello")))
                .andExpect(status().is3xxRedirection());
    }

    // ---- Valid authenticated request ----

    @Test
    void chat_authenticatedClinician_ownEncounter_returnsOk() throws Exception {
        when(aiService.chat(any(), anyString()))
                .thenReturn(AiChatResponse.reply("How long have you had this headache?"));

        mockMvc.perform(post("/api/ai/chat")
                .with(user(clinician.getUsername()).roles("DOCTOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson("My head hurts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("How long have you had this headache?"))
                .andExpect(jsonPath("$.complete").value(false));
    }

    @Test
    void chat_adminUser_canAccessAnyEncounter() throws Exception {
        when(aiService.chat(any(), anyString()))
                .thenReturn(AiChatResponse.reply("Describe the pain."));

        mockMvc.perform(post("/api/ai/chat")
                .with(user("admin.ai").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson("Hello")))
                .andExpect(status().isOk());
    }

    // ---- Authorization ----

    @Test
    void chat_differentClinician_notAssignedToEncounter_isForbidden() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                .with(user(otherUser.getUsername()).roles("NURSE"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson("Hello")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    // ---- Invalid encounter ----

    @Test
    void chat_nonExistentEncounterId_returnsNotFound() throws Exception {
        String body = objectMapper.writeValueAsString(buildRequest(999999L, "Hello"));

        mockMvc.perform(post("/api/ai/chat")
                .with(user(clinician.getUsername()).roles("DOCTOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    // ---- Empty message ----

    @Test
    void chat_emptyUserMessage_returnsBadRequest() throws Exception {
        String body = objectMapper.writeValueAsString(buildRequest(encounter.getId(), ""));

        mockMvc.perform(post("/api/ai/chat")
                .with(user(clinician.getUsername()).roles("DOCTOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    // ---- AI disabled ----

    @Test
    void chat_whenAiDisabled_returnsDisabledResponse() throws Exception {
        when(aiService.chat(any(), anyString()))
                .thenReturn(AiChatResponse.disabled(
                        "AI assistance is not configured. Set AI_ENABLED=true and provide AI_API_KEY."));

        mockMvc.perform(post("/api/ai/chat")
                .with(user(clinician.getUsername()).roles("DOCTOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson("Hello")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disabled").value(true))
                .andExpect(jsonPath("$.reply").value(
                        org.hamcrest.Matchers.containsString("not configured")));
    }

    // ---- Provider failure ----

    @Test
    void chat_whenProviderFails_returnsSafeError() throws Exception {
        when(aiService.chat(any(), anyString()))
                .thenReturn(AiChatResponse.error(
                        "AI service is temporarily unavailable. Please try again or proceed with manual case-taking."));

        mockMvc.perform(post("/api/ai/chat")
                .with(user(clinician.getUsername()).roles("DOCTOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson("Hello")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").exists());
    }

    // ---- Complete response with valid structured data → draftReady ----

    @Test
    void chat_whenComplete_validDraft_returnsDraftReady() throws Exception {
        String structuredJson = "{\"chiefComplaint\":\"Headache\",\"symptoms\":[]}";
        when(aiService.chat(any(), anyString()))
                .thenReturn(AiChatResponse.complete("Here is the preliminary draft.", structuredJson));

        mockMvc.perform(post("/api/ai/chat")
                .with(user(clinician.getUsername()).roles("DOCTOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson("That's all")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.draftReady").value(true))
                .andExpect(jsonPath("$.reply").value("Here is the preliminary draft."));
    }

    // ---- Complete response with unsafe structured data → plain reply, no draft ----

    @Test
    void chat_whenComplete_unsafeDraft_returnsPlainReply() throws Exception {
        // Simulate the validator rejecting the draft (contains diagnoses)
        when(sessionService.saveDraft(anyLong(), anyString()))
                .thenThrow(new AiDraftValidator.AiDraftValidationException(
                        "AI draft contains diagnoses — this is prohibited."));

        String structuredJson = "{\"chiefComplaint\":\"Fever\",\"diagnoses\":[\"Malaria\"]}";
        when(aiService.chat(any(), anyString()))
                .thenReturn(AiChatResponse.complete("I have gathered enough information.", structuredJson));

        mockMvc.perform(post("/api/ai/chat")
                .with(user(clinician.getUsername()).roles("DOCTOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson("I think I have malaria")))
                .andExpect(status().isOk())
                // Should NOT return draftReady=true when draft is rejected
                .andExpect(jsonPath("$.draftReady").value(false))
                // Should return a safe plain reply, not an error
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    // ---- Conversation persisted: server history used, not client history ----

    @Test
    void chat_serverHistoryUsedNotClientHistory() throws Exception {
        // Server returns a 2-turn history — AI should receive this, not the empty client history
        List<AiChatRequest.Message> serverHistory = List.of(
                new AiChatRequest.Message("user", "Hello"),
                new AiChatRequest.Message("assistant", "Hi there"));
        when(sessionService.getConversationHistory(anyLong())).thenReturn(serverHistory);
        when(aiService.chat(eq(serverHistory), anyString()))
                .thenReturn(AiChatResponse.reply("What brings you in?"));

        mockMvc.perform(post("/api/ai/chat")
                .with(user(clinician.getUsername()).roles("DOCTOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                // Client sends empty history — server history must take precedence
                .content(validRequestJson("More details")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("What brings you in?"));
    }

    // ---- Helpers ----

    private String validRequestJson(String message) throws Exception {
        return objectMapper.writeValueAsString(buildRequest(encounter.getId(), message));
    }

    private AiChatRequest buildRequest(Long encounterId, String message) {
        AiChatRequest req = new AiChatRequest();
        req.setEncounterId(encounterId);
        req.setUserMessage(message);
        req.setConversationHistory(List.of());
        return req;
    }

    private User saveUser(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPasswordHash(passwordEncoder.encode("Pass@1"));
        u.setFirstName("AI"); u.setLastName("Test");
        u.setRole(role); u.setEnabled(true);
        return userRepository.save(u);
    }
}
