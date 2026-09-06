package com.patientcase.kiosk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientcase.api.kiosk.KioskChatRequest;
import com.patientcase.consent.ConsentService;
import com.patientcase.ai.AiChatResponse;
import com.patientcase.ai.AiChatService;
import com.patientcase.document.Document;
import com.patientcase.document.DocumentExtraction;
import com.patientcase.document.DocumentExtractionRepository;
import com.patientcase.document.DocumentRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * End-to-end tests for the patient kiosk portal (routes, security boundaries,
 * and the /api/kiosk/chat endpoint).
 *
 * @MockBean AiChatService (interface) avoids real calls to the AI provider.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class KioskFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private KioskIntakeService intakeService;
    @Autowired private ConsentService consentService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentExtractionRepository documentExtractionRepository;

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
    void kioskPublicPages_arePubliclyVisible() throws Exception {
        mockMvc.perform(get("/kiosk")).andExpect(status().isOk())
                .andExpect(view().name("kiosk/index"));
        mockMvc.perform(get("/kiosk/login")).andExpect(status().isOk())
                .andExpect(view().name("kiosk/login"));
        mockMvc.perform(get("/kiosk/register")).andExpect(status().isOk())
                .andExpect(view().name("kiosk/register"));
    }

    @Test
    void kioskHome_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/kiosk/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login**"));
    }

    @Test
    void kioskHome_asStaff_isForbidden() throws Exception {
        mockMvc.perform(get("/kiosk/home").with(user("dr.smith").roles("DOCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_post_autoLoginThenConsentAccessible() throws Exception {
        MvcResult result = mockMvc.perform(post("/kiosk/register")
                        .with(csrf())
                        .param("firstName", "Auto")
                        .param("lastName", "Login")
                        .param("dateOfBirth", "1990-01-01")
                        .param("gender", "FEMALE")
                        .param("phone", "9876501234")
                        .param("email", "autologin@test.com")
                        .param("address", "Kiosk Street")
                        .param("username", "autologin.patient")
                        .param("patientNumber", "AUTO-01")
                        .param("password", "StrongPass#1")
                        .param("confirmPassword", "StrongPass#1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kiosk/consent"))
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) result.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session);

        // Same session must be authenticated: consent is only reachable for PATIENT
        mockMvc.perform(get("/kiosk/consent").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("kiosk/consent"));
    }

    @Test
    void kioskHome_asPatient_returnsOk() throws Exception {
        createPatient();
        mockMvc.perform(get("/kiosk/home")
                        .with(user(patientUser.getUsername()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("kiosk/home"))
                .andExpect(model().attributeExists("patient", "activeIntake"));
    }

    @Test
    void intakePage_afterConsent_returnsOk() throws Exception {
        createPatientWithConsentedIntake();
        mockMvc.perform(get("/kiosk/intake/{id}", intake.getId())
                        .with(user(patientUser.getUsername()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("kiosk/intake"));
    }

    @Test
    void consentPage_asPatient_returnsOk() throws Exception {
        createPatient();
        mockMvc.perform(get("/kiosk/consent")
                        .with(user(patientUser.getUsername()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("kiosk/consent"));
    }

    @Test
    void ayushPage_asPatient_returnsOk() throws Exception {
        createPatientWithConsentedIntake();
        mockMvc.perform(get("/kiosk/intake/{id}/ayush", intake.getId())
                        .with(user(patientUser.getUsername()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("kiosk/ayush"));
    }

    @Test
    void documentsPage_asPatient_returnsOk() throws Exception {
        createPatientWithConsentedIntake();
        mockMvc.perform(get("/kiosk/intake/{id}/documents", intake.getId())
                        .with(user(patientUser.getUsername()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("kiosk/documents"));
    }

    @Test
    void summaryPage_asPatient_returnsOk() throws Exception {
        createPatientWithConsentedIntake();
        mockMvc.perform(get("/kiosk/intake/{id}/summary", intake.getId())
                        .with(user(patientUser.getUsername()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("kiosk/summary"));
    }

    @Test
    void reviewPage_asDoctor_showsIntakeDetails() throws Exception {
        createSubmittedIntake();
        mockMvc.perform(get("/intakes/{id}", intake.getId())
                        .with(user("dr.review").roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("kiosk/intakes/review"))
                .andExpect(model().attributeExists("draft", "redFlags", "patient", "documents"));
    }

    // ── /intakes review queue scoping ─────────────────────────────────────────

    @Test
    void intakesQueue_asDoctor_returnsOk() throws Exception {
        mockMvc.perform(get("/intakes").with(user("dr.review").roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("kiosk/intakes/review-queue"));
    }

    @Test
    void intakesQueue_asPatient_isForbidden() throws Exception {
        mockMvc.perform(get("/intakes").with(user("some.patient").roles("PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void intakesQueue_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/intakes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login**"));
    }

    // ── Kiosk documents (upload + patient-scoped download) ──────────────────

    @Test
    void documentsUpload_asPatient_redirectsAndListsFile() throws Exception {
        createPatientWithConsentedIntake();
        uploadFileViaUi();
        mockMvc.perform(get("/kiosk/intake/{id}/documents", intake.getId())
                        .with(user(patientUser.getUsername()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("kiosk/documents"))
                .andExpect(content().string(containsString("prior-records.pdf")));
    }

    @Test
    void documentsPage_rendersCompletedExtraction_returns200Not500() throws Exception {
        createPatientWithConsentedIntake();
        long docId = uploadFileViaUi();

        // The upload already requests digitization; force a known COMPLETED result
        // so the status column renders (regression for the provider-expression 500).
        DocumentExtraction extraction = documentExtractionRepository.findByDocumentId(docId)
                .orElseGet(DocumentExtraction::new);
        extraction.setDocument(documentRepository.findById(docId).orElseThrow());
        extraction.setIntake(intakeService.requireOwnedIntake(intake.getId(), patient.getId()));
        extraction.setStatus(DocumentExtraction.Status.COMPLETED);
        extraction.setProvider("FAKE_PDF_OCR");
        extraction.setExtractedJson("{}");
        documentExtractionRepository.save(extraction);

        mockMvc.perform(get("/kiosk/intake/{id}/documents", intake.getId())
                        .with(user(patientUser.getUsername()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Provider: FAKE_PDF_OCR")));
    }

    @Test
    void kioskDocumentDownload_asOwner_streamsFile() throws Exception {
        createPatientWithConsentedIntake();
        long docId = uploadFileViaUi();
        mockMvc.perform(get("/kiosk/document/{id}/download", docId)
                        .with(user(patientUser.getUsername()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("prior-records.pdf")));
    }

    @Test
    void kioskDocumentDownload_anotherPatientsDocument_is404() throws Exception {
        createPatientWithConsentedIntake();
        long docId = uploadFileViaUi();

        User otherUser = saveUser("kiosk.docother", Role.PATIENT);
        Patient otherPatient = savePatient("P-FLOW-99", otherUser);

        mockMvc.perform(get("/kiosk/document/{id}/download", docId)
                        .with(user(otherUser.getUsername()).roles("PATIENT")))
                .andExpect(status().isNotFound());
    }

    // ── /api/kiosk/chat authorization ─────────────────────────────────────────

    @Test
    void chat_asStaff_isForbidden() throws Exception {
        mockMvc.perform(post("/api/kiosk/chat")
                        .with(user("dr.smith").roles("DOCTOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChatBody(null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void chat_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/kiosk/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChatBody(null)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void chat_asPatient_withoutConsent_returnsError() throws Exception {
        createPatient();
        mockMvc.perform(post("/api/kiosk/chat")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChatBody(intake.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("Consent")));
    }

    @Test
    void chat_asPatient_withConsent_returnsReply() throws Exception {
        createPatientWithConsentedIntake();
        mockMvc.perform(post("/api/kiosk/chat")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChatBody(intake.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("How long have you had this?"))
                .andExpect(jsonPath("$.complete").value(false));
    }

    @Test
    void chat_completeValidDraft_setsDraftReady() throws Exception {
        createPatientWithConsentedIntake();
        when(aiService.chat(any(), anyString()))
                .thenReturn(AiChatResponse.complete(
                        "Here is your summary.",
                        "{\"chiefComplaint\":\"Headache\",\"symptoms\":[]}"));

        mockMvc.perform(post("/api/kiosk/chat")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChatBody(intake.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.draftReady").value(true));

        // Draft and red-flag data persisted server-side
        KioskIntake stored = intakeService.requireOwnedIntake(intake.getId(), patient.getId());
        org.junit.jupiter.api.Assertions.assertEquals(
                KioskIntakeStatus.DRAFT_READY, stored.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(stored.getDraftJson().contains("Headache"));
    }

    @Test
    void chat_emptyMessage_returnsBadRequest() throws Exception {
        createPatientWithConsentedIntake();
        mockMvc.perform(post("/api/kiosk/chat")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChatBodyWithMessage(intake.getId(), "  ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_nonOwnedIntake_returnsNotFound() throws Exception {
        createPatientWithConsentedIntake();
        User otherUser = saveUser("kiosk.other", Role.PATIENT);
        Patient otherPatient = savePatient("P-FLOW-02", otherUser);
        KioskIntake foreignIntake =
                intakeService.getOrCreateActiveIntake(otherPatient.getId(), otherUser.getId());

        mockMvc.perform(post("/api/kiosk/chat")
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChatBody(foreignIntake.getId())))
                .andExpect(status().isNotFound());
    }

    // ── Guided intake (AI-off primary flow) ──────────────────────────────────

    @Test
    void intakePage_aiDisabled_rendersGuidedWizardWithoutChat() throws Exception {
        // app.ai.enabled defaults to false in the test profile.
        createPatient();
        mockMvc.perform(get("/kiosk/intake/{id}", intake.getId())
                        .with(user(patientUser.getUsername()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("kiosk/intake"))
                .andExpect(content().string(containsString("Guided intake")))
                .andExpect(content().string(containsString("guided questions above")))
                .andExpect(content().string(containsString("What brings you in today?")))
                .andExpect(model().attribute("aiEnabled", false));
    }

    @Test
    void guidedCasePost_savesDraftReadyAndRedirectsToSummary() throws Exception {
        createPatientWithConsentedIntake();
        mockMvc.perform(post("/kiosk/intake/{id}/case", intake.getId())
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .param("chiefComplaint", "Right knee pain")
                        .param("complaintDetail", "Worse in the mornings")
                        .param("symptomOnset", "GRADUAL")
                        .param("symptomDuration", "3 days")
                        .param("symptomSeverity", "MODERATE")
                        .param("hpi", "Pain while climbing stairs")
                        .param("aggravating", "Walking")
                        .param("relieving", "Rest")
                        .param("associatedSymptoms", "Nausea")
                        .param("associatedSymptoms", "Fever")
                        .param("pastMedicalHistory", "Diabetes")
                        .param("currentMedications", "Metformin 500mg")
                        .param("allergies", "Penicillin")
                        .param("familyHistory", "None")
                        .param("habits", "Non-smoker")
                        .param("safetySignals", "Chest pain or pressure")
                        .param("safetySignals", "High fever")
                        .param("additionalNotes", "Called ahead"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kiosk/intake/" + intake.getId() + "/summary"));

        KioskIntake stored = intakeService.requireOwnedIntake(intake.getId(), patient.getId());
        org.junit.jupiter.api.Assertions.assertEquals(KioskIntakeStatus.DRAFT_READY, stored.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(stored.getDraftJson().contains("Right knee pain"));
        org.junit.jupiter.api.Assertions.assertTrue(stored.getDraftJson().contains("Chest pain or pressure"));
    }

    @Test
    void guidedCasePost_withoutConsent_redirectsBackWithError() throws Exception {
        createPatient();
        mockMvc.perform(post("/kiosk/intake/{id}/case", intake.getId())
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .param("chiefComplaint", "Fever"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kiosk/intake/" + intake.getId()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("errorMessage",
                                org.hamcrest.Matchers.containsString("consent")));
    }

    @Test
    void guidedCasePost_nonOwnedIntake_isForbidden() throws Exception {
        createPatientWithConsentedIntake();
        User otherUser = saveUser("kiosk.flowcase", Role.PATIENT);
        Patient otherPatient = savePatient("P-FLOW-03", otherUser);
        KioskIntake foreignIntake =
                intakeService.getOrCreateActiveIntake(otherPatient.getId(), otherUser.getId());

        mockMvc.perform(post("/kiosk/intake/{id}/case", foreignIntake.getId())
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf())
                        .param("chiefComplaint", "Fever"))
                .andExpect(status().isForbidden());
    }

    // ── Receptionist role gating (F3–F5) ──────────────────────────────────────

    @Test
    void dashboard_asReceptionist_hidesEncounterManagement() throws Exception {
        mockMvc.perform(get("/dashboard").with(user("rec.one").roles("RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Appointments")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/encounters/"))));
    }

    @Test
    void profile_asReceptionist_hidesCaseManagementUi() throws Exception {
        User u = saveUser("rec.patient", Role.RECEPTIONIST);
        Patient p = savePatient("P-FLOW-04", u);
        mockMvc.perform(get("/patients/{id}", p.getId()).with(user("rec.view").roles("RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(p.getFullName())))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/cases/new"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("createCaseModal"))));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void createPatient() {
        patientUser = saveUser("kiosk.flow", Role.PATIENT);
        patient = savePatient("P-FLOW-01", patientUser);
        intake = intakeService.getOrCreateActiveIntake(patient.getId(), patientUser.getId());
    }

    private void createPatientWithConsentedIntake() {
        createPatient();
        consentService.grant(patient.getId(), patientUser.getId(),
                "patient_intake", "medikiosk-1.0", "127.0.0.1");
        intake = intakeService.getOrCreateActiveIntake(patient.getId(), patientUser.getId());
        intakeService.bindConsent(intake.getId(), patient.getId(),
                consentService.findByPatientId(patient.getId()).get(0));
    }

    private void createSubmittedIntake() {
        createPatientWithConsentedIntake();
        intakeService.saveDraft(intake.getId(), patient.getId(),
                "{\"chiefComplaint\":\"Fever\",\"symptoms\":[],\"redFlags\":[\"High fever for 3 days\"]}");
        intakeService.submit(intake.getId(), patient.getId());
    }

    private long uploadFileViaUi() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "prior-records.pdf", "application/pdf",
                "%PDF-1.4 fake-probe-content".getBytes());
        mockMvc.perform(multipart("/kiosk/intake/{id}/documents", intake.getId())
                        .file(file)
                        .with(user(patientUser.getUsername()).roles("PATIENT"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        List<Document> docs = documentRepository.findByPatientIdOrderByUploadedAtDesc(patient.getId());
        org.junit.jupiter.api.Assertions.assertFalse(docs.isEmpty());
        return docs.get(0).getId();
    }

    private String validChatBody(Long intakeId) {
        return validChatBodyWithMessage(intakeId, "I have a headache");
    }

    private String validChatBodyWithMessage(Long intakeId, String message) {
        KioskChatRequest req = new KioskChatRequest();
        req.setIntakeId(intakeId);
        req.setUserMessage(message);
        try {
            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private User saveUser(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPasswordHash(passwordEncoder.encode("Pass@1"));
        u.setFirstName("Flow"); u.setLastName("Test");
        u.setRole(role); u.setEnabled(true);
        return userRepository.save(u);
    }

    private Patient savePatient(String number, User user) {
        Patient p = new Patient();
        p.setPatientNumber(number);
        p.setUser(user);
        p.setFirstName("Flow"); p.setLastName("Patient");
        p.setDateOfBirth(LocalDate.of(1990, 6, 15));
        p.setGender(Gender.FEMALE);
        p.setEmail(user.getEmail());
        return patientRepository.save(p);
    }
}