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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /encounters/{id}/ai-intake.
 *
 * Uses a real Spring context and H2 in-memory DB (profile "test").
 * All DB state is rolled back after each test.
 *
 * No @MockBean — EncounterService is concrete and would require Byte Buddy
 * subclassing (incompatible with Java 25). Tests rely on the real service
 * with DB-backed encounters instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiIntakeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PatientCaseRepository caseRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User assignedClinician;
    private User otherClinician;
    private Encounter encounter;

    @BeforeEach
    void setUp() {
        assignedClinician = saveUser("intake.doctor", Role.DOCTOR);
        otherClinician    = saveUser("intake.other",  Role.NURSE);

        Patient patient = new Patient();
        patient.setPatientNumber("P-INT-01");
        patient.setFirstName("Intake"); patient.setLastName("Patient");
        patient.setDateOfBirth(LocalDate.of(1990, 6, 15));
        patient.setGender(Gender.FEMALE);
        patientRepository.save(patient);

        PatientCase patientCase = new PatientCase();
        patientCase.setCaseNumber("C-INT-001");
        patientCase.setPatient(patient);
        patientCase.setTitle("Intake Test Case");
        patientCase.setChiefComplaint("Headache");
        patientCase.setStatus(CaseStatus.IN_PROGRESS);
        patientCase.setPriority(CasePriority.MEDIUM);
        caseRepository.save(patientCase);

        encounter = new Encounter();
        encounter.setPatientCase(patientCase);
        encounter.setClinician(assignedClinician);
        encounter.setStatus(EncounterStatus.DRAFT);
        encounterRepository.save(encounter);
    }

    // ── Authentication ────────────────────────────────────────────────────────

    @Test
    void aiIntake_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/encounters/{id}/ai-intake", encounter.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login**"));
    }

    // ── Role-based URL-level access ───────────────────────────────────────────

    @Test
    void aiIntake_asReceptionist_isForbidden() throws Exception {
        // RECEPTIONIST is not in the /encounters/** allow-list
        mockMvc.perform(get("/encounters/{id}/ai-intake", encounter.getId())
                .with(user(assignedClinician.getUsername()).roles("RECEPTIONIST")))
                .andExpect(status().isForbidden());
    }

    // ── Authorization: assigned clinician ─────────────────────────────────────

    @Test
    void aiIntake_assignedClinician_returnsOk() throws Exception {
        mockMvc.perform(get("/encounters/{id}/ai-intake", encounter.getId())
                .with(user(assignedClinician.getUsername()).roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("encounters/ai-intake"))
                .andExpect(model().attributeExists("encounter"));
    }

    // ── Authorization: admin can access any encounter ─────────────────────────

    @Test
    void aiIntake_adminUser_returnsOk() throws Exception {
        mockMvc.perform(get("/encounters/{id}/ai-intake", encounter.getId())
                .with(user("admin.intake").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("encounters/ai-intake"));
    }

    // ── Authorization: other clinician not assigned to the encounter ──────────

    @Test
    void aiIntake_otherClinician_notAssigned_isForbidden() throws Exception {
        mockMvc.perform(get("/encounters/{id}/ai-intake", encounter.getId())
                .with(user(otherClinician.getUsername()).roles("NURSE")))
                .andExpect(status().isForbidden());
    }

    // ── Not found ─────────────────────────────────────────────────────────────

    @Test
    void aiIntake_nonExistentEncounter_returnsNotFound() throws Exception {
        mockMvc.perform(get("/encounters/{id}/ai-intake", 999999L)
                .with(user(assignedClinician.getUsername()).roles("DOCTOR")))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User saveUser(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPasswordHash(passwordEncoder.encode("Pass@1"));
        u.setFirstName("Intake"); u.setLastName("Test");
        u.setRole(role); u.setEnabled(true);
        return userRepository.save(u);
    }
}
