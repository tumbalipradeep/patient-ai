package com.patientcase.kiosk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientcase.ai.AiChatResponse;
import com.patientcase.ai.AiChatService;
import com.patientcase.api.kiosk.KioskChatRequest;
import com.patientcase.api.kiosk.KioskResetRequest;
import com.patientcase.consent.ConsentService;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * End-to-end tests for the MediKiosk Clinical History Assistant.
 *
 * The chat turn protocol, idempotency (clientTurnId), the reset endpoint and
 * server-side conversation rendering on the intake page are all covered here.
 * AI-enabled rendering is forced via @SpringBootTest(properties=...) while the
 * test profile keeps the AI provider mocked so nothing real is called.
 */
@SpringBootTest(properties = "app.ai.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class KioskAiConversationFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private KioskIntakeService intakeService;
    @Autowired private ConsentService consentService;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockBean private AiChatService aiService;

    private User patientUser;
    private Patient patient;
    private KioskIntake intake;

    @BeforeEach
    void setUp() {
        when(aiService.chat(any(), anyString()))
                .thenReturn(AiChatResponse.reply("How long have you had this?"));
    }

    @Test
    void intakePage_aiEnabled_rendersAssistantPanel() throws Exception {
        createPatientWithConsentedIntake();
        mockMvc.perform(get("/kiosk/intake/{id}", intake.getId())
                        .with(user(patientUser.getUsername()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("kiosk/intake"))
                .andExpect(model().attribute("aiEnabled", true))
                .andExpect(model().attributeExists("aiHistory"))
                .andExpect(content().string(containsString("MediKiosk Clinical History Assistant")))
                .andExpect(content().string(containsString("ai-section-label")))
                .andExpect(content().string(containsString("chat-chips")))
                .andExpect(content().string(containsString("btn-reset")))
                // The AI-off guidance must NOT appear when AI is enabled
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("conversational assistant is not available"))));
    }

    @Test
    void intakePage_aiEnabled_withHistory_rendersServerConversation() throws Exception {
        createPatientWithConsentedIntake();
        sendChatTurn(intake.getId(), "I have a headache", "t-turn-1");

        mockMvc.perform(get("/kiosk/intake/{id}", intake.getId())
                        .with(user(patientUser.getUsername()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("aiHistory", hasSize(2)))
                // Both the patient answer and the assistant reply are rendered server-side
                .andExpect(content().string(containsString("I have a headache")))
                .andExpect(content().string(containsString("How long have you had this?")));
    }

    @Test
    void chat_sameClientTurnIdTwice_callsProviderOnlyOnce() throws Exception {
        createPatientWithConsentedIntake();
        String body = chatBody(intake.getId(), "I have a headache", "t-duplicate");

        mockMvc.perform(post("/api/kiosk/chat")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("How long have you had this?"));

        // Duplicate submit of the same turn: answered from the server's stored
        // record — provider must NOT be called a second time.
        mockMvc.perform(post("/api/kiosk/chat")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("How long have you had this?"));

        verify(aiService, times(1)).chat(any(), anyString());

        // Duplicate turn must NOT double-append the history (still 2 messages)
        assertThatHistorySize(2);
    }

    @Test
    void chat_differentTurnIds_appendsEachTurn() throws Exception {
        createPatientWithConsentedIntake();
        sendChatTurn(intake.getId(), "I have a headache", "t-a1");
        sendChatTurn(intake.getId(), "Since this morning", "t-b2");
        assertThatHistorySize(4);
    }

    @Test
    void chat_providerError_isRetryable() throws Exception {
        createPatientWithConsentedIntake();
        when(aiService.chat(any(), anyString()))
                .thenReturn(AiChatResponse.error(
                        "AI service is temporarily unavailable.").retryable());

        mockMvc.perform(post("/api/kiosk/chat")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody(intake.getId(), "I have a headache", "t-retry")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error", containsString("temporarily unavailable")))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void chat_retryAfterError_sameTurnRecordedOnce() throws Exception {
        createPatientWithConsentedIntake();
        when(aiService.chat(any(), anyString()))
                .thenReturn(AiChatResponse.error("boom").retryable())
                .thenReturn(AiChatResponse.reply("How long have you had this?"));

        // First attempt fails (error, no history persisted)
        mockMvc.perform(post("/api/kiosk/chat")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody(intake.getId(), "I have a headache", "t-retry2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("boom"));

        // Retry with the SAME clientTurnId succeeds and records one turn
        mockMvc.perform(post("/api/kiosk/chat")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody(intake.getId(), "I have a headache", "t-retry2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("How long have you had this?"));

        assertThatHistorySize(2);
    }

    @Test
    void reset_clearsConversationHistory() throws Exception {
        createPatientWithConsentedIntake();
        sendChatTurn(intake.getId(), "I have a headache", "t-reset");
        assertThatHistorySize(2);

        mockMvc.perform(post("/api/kiosk/chat/reset")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(intake.getId())))
                .andExpect(status().isOk());

        assertThatHistorySize(0);
        // Intake itself remains IN_PROGRESS — reset only clears the conversation
        org.junit.jupiter.api.Assertions.assertEquals(
                KioskIntakeStatus.IN_PROGRESS,
                intakeService.requireOwnedIntake(intake.getId(), patient.getId()).getStatus());
    }

    @Test
    void reset_withoutConsent_isForbidden() throws Exception {
        createPatient();
        mockMvc.perform(post("/api/kiosk/chat/reset")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(intake.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void reset_afterDraftReadiness_conflict() throws Exception {
        createPatientWithConsentedIntake();
        intakeService.saveDraft(intake.getId(), patient.getId(),
                "{\"chiefComplaint\":\"Fever\",\"symptoms\":[]}");
        mockMvc.perform(post("/api/kiosk/chat/reset")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(intake.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void reset_nonOwnedIntake_isNotFound() throws Exception {
        createPatientWithConsentedIntake();
        User otherUser = saveUser("kiosk.ai.other", Role.PATIENT);
        Patient otherPatient = savePatient("P-KAI-02", otherUser);
        KioskIntake foreignIntake =
                intakeService.getOrCreateActiveIntake(otherPatient.getId(), otherUser.getId());
        consentService.grant(otherPatient.getId(), otherUser.getId(),
                "patient_intake", "medikiosk-1.0", "127.0.0.1");
        intakeService.bindConsent(foreignIntake.getId(), otherPatient.getId(),
                consentService.findByPatientId(otherPatient.getId()).get(0));

        mockMvc.perform(post("/api/kiosk/chat/reset")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(foreignIntake.getId())))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendChatTurn(Long intakeId, String message, String clientTurnId) throws Exception {
        mockMvc.perform(post("/api/kiosk/chat")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody(intakeId, message, clientTurnId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("How long have you had this?"));
    }

    private void assertThatHistorySize(int expected) {
        org.junit.jupiter.api.Assertions.assertEquals(
                expected,
                intakeService.getConversationMessages(intake.getId(), patient.getId()).size());
    }

    private String chatBody(Long intakeId, String message, String clientTurnId) throws Exception {
        KioskChatRequest req = new KioskChatRequest();
        req.setIntakeId(intakeId);
        req.setUserMessage(message);
        req.setClientTurnId(clientTurnId);
        return objectMapper.writeValueAsString(req);
    }

    private String resetBody(Long intakeId) throws Exception {
        KioskResetRequest req = new KioskResetRequest();
        req.setIntakeId(intakeId);
        return objectMapper.writeValueAsString(req);
    }

    private void createPatient() {
        patientUser = saveUser("kiosk.ai.flow", Role.PATIENT);
        patient = savePatient("P-KAI-01", patientUser);
        intake = intakeService.getOrCreateActiveIntake(patient.getId(), patientUser.getId());
    }

    private void createPatientWithConsentedIntake() {
        createPatient();
        consentService.grant(patient.getId(), patientUser.getId(),
                "patient_intake", "medikiosk-1.0", "127.0.0.1");
        intakeService.bindConsent(intake.getId(), patient.getId(),
                consentService.findByPatientId(patient.getId()).get(0));
    }

    private User saveUser(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPasswordHash(passwordEncoder.encode("Pass@1"));
        u.setFirstName("Ai"); u.setLastName("Flow");
        u.setRole(role); u.setEnabled(true);
        return userRepository.save(u);
    }

    private Patient savePatient(String number, User user) {
        Patient p = new Patient();
        p.setPatientNumber(number);
        p.setUser(user);
        p.setFirstName("Ai"); p.setLastName("Patient");
        p.setDateOfBirth(LocalDate.of(1990, 6, 15));
        p.setGender(com.patientcase.patient.Gender.FEMALE);
        p.setEmail(user.getEmail());
        return patientRepository.save(p);
    }
}