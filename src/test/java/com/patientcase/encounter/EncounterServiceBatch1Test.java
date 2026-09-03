package com.patientcase.encounter;

import com.patientcase.case_management.CasePriority;
import com.patientcase.case_management.CaseStatus;
import com.patientcase.case_management.PatientCase;
import com.patientcase.case_management.PatientCaseRepository;
import com.patientcase.clinical.FollowUp;
import com.patientcase.clinical.FollowUpRepository;
import com.patientcase.clinical.FollowUpStatus;
import com.patientcase.common.ResourceNotFoundException;
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
class EncounterServiceBatch1Test {

    @Autowired private EncounterService encounterService;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private PatientCaseRepository caseRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FollowUpRepository followUpRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private PatientCase testCase;
    private User testClinician;

    @BeforeEach
    void setUp() {
        Patient patient = new Patient();
        patient.setPatientNumber("P-B1-01");
        patient.setFirstName("Batch");
        patient.setLastName("One");
        patient.setDateOfBirth(LocalDate.of(1980, 1, 1));
        patient.setGender(Gender.MALE);
        patientRepository.save(patient);

        testClinician = new User();
        testClinician.setUsername("b1.doctor");
        testClinician.setEmail("b1.doctor@test.com");
        testClinician.setPasswordHash(passwordEncoder.encode("password"));
        testClinician.setFirstName("B1");
        testClinician.setLastName("Doctor");
        testClinician.setRole(Role.DOCTOR);
        testClinician.setEnabled(true);
        userRepository.save(testClinician);

        testCase = new PatientCase();
        testCase.setCaseNumber("C-B1-001");
        testCase.setPatient(patient);
        testCase.setTitle("Batch 1 Test Case");
        testCase.setChiefComplaint("Test");
        testCase.setStatus(CaseStatus.OPEN);
        testCase.setPriority(CasePriority.MEDIUM);
        caseRepository.save(testCase);
    }

    // --- Encounter Cancellation ---

    @Test
    void cancelEncounter_draftEncounter_shouldSetStatusCancelled() {
        Encounter encounter = createDraftEncounter();
        encounterService.cancelEncounter(encounter.getId());

        Encounter updated = encounterRepository.findById(encounter.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(EncounterStatus.CANCELLED);
    }

    @Test
    void cancelEncounter_completedEncounter_shouldThrow() {
        Encounter encounter = createDraftEncounter();
        encounter.setStatus(EncounterStatus.COMPLETED);
        encounterRepository.save(encounter);

        assertThatThrownBy(() -> encounterService.cancelEncounter(encounter.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed encounter cannot be cancelled");
    }

    @Test
    void cancelEncounter_alreadyCancelled_shouldThrow() {
        Encounter encounter = createDraftEncounter();
        encounter.setStatus(EncounterStatus.CANCELLED);
        encounterRepository.save(encounter);

        assertThatThrownBy(() -> encounterService.cancelEncounter(encounter.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already cancelled");
    }

    // --- Follow-up date validation ---

    @Test
    void saveCaseTaking_withInvalidFollowUpDate_shouldThrow() {
        Encounter encounter = createDraftEncounter();
        CaseTakingForm form = new CaseTakingForm();
        form.setFollowUpDate("not-a-date");

        assertThatThrownBy(() -> encounterService.saveCaseTaking(encounter.getId(), form, "b1.doctor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid date");
    }

    @Test
    void saveCaseTaking_withValidFollowUpDate_shouldPersist() {
        Encounter encounter = createDraftEncounter();
        CaseTakingForm form = new CaseTakingForm();
        form.setFollowUpDate(LocalDate.now().plusDays(7).toString());
        form.setFollowUpInstructions("Return if symptoms worsen");

        encounterService.saveCaseTaking(encounter.getId(), form, "b1.doctor");

        var followUps = followUpRepository.findByEncounterId(encounter.getId());
        assertThat(followUps).hasSize(1);
        assertThat(followUps.get(0).getFollowUpDate()).isEqualTo(LocalDate.now().plusDays(7));
        assertThat(followUps.get(0).getStatus()).isEqualTo(FollowUpStatus.PENDING);
    }

    // --- Follow-up status update ---

    @Test
    void updateFollowUpStatus_pendingToCompleted_shouldSucceed() {
        Encounter encounter = createDraftEncounter();

        FollowUp followUp = new FollowUp();
        followUp.setEncounter(encounter);
        followUp.setPatientCase(testCase);
        followUp.setFollowUpDate(LocalDate.now().plusDays(3));
        followUp.setStatus(FollowUpStatus.PENDING);
        followUpRepository.save(followUp);

        encounterService.updateFollowUpStatus(followUp.getId(), FollowUpStatus.COMPLETED);

        FollowUp updated = followUpRepository.findById(followUp.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(FollowUpStatus.COMPLETED);
    }

    @Test
    void updateFollowUpStatus_completedToPending_shouldThrow() {
        Encounter encounter = createDraftEncounter();

        FollowUp followUp = new FollowUp();
        followUp.setEncounter(encounter);
        followUp.setPatientCase(testCase);
        followUp.setStatus(FollowUpStatus.COMPLETED);
        followUpRepository.save(followUp);

        assertThatThrownBy(() -> encounterService.updateFollowUpStatus(followUp.getId(), FollowUpStatus.PENDING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed follow-up cannot be changed");
    }

    @Test
    void updateFollowUpStatus_nonExistentId_shouldThrow() {
        assertThatThrownBy(() -> encounterService.updateFollowUpStatus(999999L, FollowUpStatus.COMPLETED))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- Helper ---

    private Encounter createDraftEncounter() {
        EncounterCreateRequest req = new EncounterCreateRequest();
        req.setCaseId(testCase.getId());
        req.setClinicianId(testClinician.getId());
        return encounterService.createEncounter(req);
    }
}
