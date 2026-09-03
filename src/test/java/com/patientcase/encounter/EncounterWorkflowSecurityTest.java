package com.patientcase.encounter;

import com.patientcase.case_management.CasePriority;
import com.patientcase.case_management.CaseStatus;
import com.patientcase.case_management.PatientCase;
import com.patientcase.case_management.PatientCaseRepository;
import com.patientcase.clinical.FollowUp;
import com.patientcase.clinical.FollowUpRepository;
import com.patientcase.clinical.FollowUpStatus;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EncounterWorkflowSecurityTest {

    @Autowired private EncounterService encounterService;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private PatientCaseRepository caseRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FollowUpRepository followUpRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private PatientCase testCase;
    private User clinician;

    @BeforeEach
    void setUp() {
        Patient patient = new Patient();
        patient.setPatientNumber("P-WF-01");
        patient.setFirstName("Workflow");
        patient.setLastName("Test");
        patient.setDateOfBirth(LocalDate.of(1985, 5, 5));
        patient.setGender(Gender.MALE);
        patientRepository.save(patient);

        clinician = new User();
        clinician.setUsername("wf.doctor");
        clinician.setEmail("wf.doctor@test.com");
        clinician.setPasswordHash(passwordEncoder.encode("x"));
        clinician.setFirstName("WF"); clinician.setLastName("Doctor");
        clinician.setRole(Role.DOCTOR); clinician.setEnabled(true);
        userRepository.save(clinician);

        testCase = new PatientCase();
        testCase.setCaseNumber("C-WF-001");
        testCase.setPatient(patient);
        testCase.setTitle("Workflow Test");
        testCase.setChiefComplaint("Test");
        testCase.setStatus(CaseStatus.OPEN);
        testCase.setPriority(CasePriority.LOW);
        caseRepository.save(testCase);
    }

    // --- saveCaseTaking guards ---

    @Test
    void saveCaseTaking_cancelledEncounter_throws() {
        Encounter encounter = createEncounter();
        encounter.setStatus(EncounterStatus.CANCELLED);
        encounterRepository.save(encounter);

        CaseTakingForm form = new CaseTakingForm();
        assertThatThrownBy(() -> encounterService.saveCaseTaking(encounter.getId(), form, "wf.doctor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancelled encounter cannot be modified");
    }

    @Test
    void saveCaseTaking_completedEncounter_throws() {
        Encounter encounter = createEncounter();
        encounter.setStatus(EncounterStatus.COMPLETED);
        encounterRepository.save(encounter);

        CaseTakingForm form = new CaseTakingForm();
        assertThatThrownBy(() -> encounterService.saveCaseTaking(encounter.getId(), form, "wf.doctor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed encounter cannot be modified");
    }

    // --- updateFollowUpStatus terminal guards ---

    @Test
    void updateFollowUpStatus_cancelledFollowUp_throws() {
        Encounter encounter = createEncounter();
        FollowUp followUp = buildFollowUp(encounter, FollowUpStatus.CANCELLED);

        assertThatThrownBy(() ->
                encounterService.updateFollowUpStatus(followUp.getId(), FollowUpStatus.PENDING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancelled follow-up cannot be changed");
    }

    @Test
    void updateFollowUpStatus_completedFollowUp_toPending_throws() {
        Encounter encounter = createEncounter();
        FollowUp followUp = buildFollowUp(encounter, FollowUpStatus.COMPLETED);

        assertThatThrownBy(() ->
                encounterService.updateFollowUpStatus(followUp.getId(), FollowUpStatus.PENDING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed follow-up cannot be changed");
    }

    @Test
    void updateFollowUpStatus_pendingToCancelled_succeeds() {
        Encounter encounter = createEncounter();
        FollowUp followUp = buildFollowUp(encounter, FollowUpStatus.PENDING);

        encounterService.updateFollowUpStatus(followUp.getId(), FollowUpStatus.CANCELLED);

        FollowUp updated = followUpRepository.findById(followUp.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(FollowUpStatus.CANCELLED);
    }

    @Test
    void updateFollowUpStatus_overdueToCompleted_succeeds() {
        Encounter encounter = createEncounter();
        FollowUp followUp = buildFollowUp(encounter, FollowUpStatus.OVERDUE);

        encounterService.updateFollowUpStatus(followUp.getId(), FollowUpStatus.COMPLETED);

        FollowUp updated = followUpRepository.findById(followUp.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(FollowUpStatus.COMPLETED);
    }

    // --- Helpers ---

    private Encounter createEncounter() {
        EncounterCreateRequest req = new EncounterCreateRequest();
        req.setCaseId(testCase.getId());
        req.setClinicianId(clinician.getId());
        return encounterService.createEncounter(req);
    }

    private FollowUp buildFollowUp(Encounter encounter, FollowUpStatus status) {
        FollowUp f = new FollowUp();
        f.setEncounter(encounter);
        f.setPatientCase(testCase);
        f.setStatus(status);
        f.setFollowUpDate(LocalDate.now().plusDays(7));
        return followUpRepository.save(f);
    }
}
