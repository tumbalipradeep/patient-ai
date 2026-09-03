package com.patientcase.encounter;

import com.patientcase.case_management.CasePriority;
import com.patientcase.case_management.CaseStatus;
import com.patientcase.case_management.PatientCase;
import com.patientcase.case_management.PatientCaseRepository;
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
class EncounterServiceTest {

    @Autowired
    private EncounterService encounterService;

    @Autowired
    private PatientCaseRepository caseRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private PatientCase testCase;
    private User testClinician;

    @BeforeEach
    void setUp() {
        // Create patient
        Patient patient = new Patient();
        patient.setPatientNumber("P-ENC-01");
        patient.setFirstName("Enc");
        patient.setLastName("Test");
        patient.setDateOfBirth(LocalDate.of(1985, 6, 15));
        patient.setGender(Gender.MALE);
        patientRepository.save(patient);

        // Create clinician
        testClinician = new User();
        testClinician.setUsername("enc.doctor");
        testClinician.setEmail("enc.doctor@test.com");
        testClinician.setPasswordHash(passwordEncoder.encode("password"));
        testClinician.setFirstName("Enc");
        testClinician.setLastName("Doctor");
        testClinician.setRole(Role.DOCTOR);
        testClinician.setEnabled(true);
        userRepository.save(testClinician);

        // Create case
        testCase = new PatientCase();
        testCase.setCaseNumber("C-ENC-001");
        testCase.setPatient(patient);
        testCase.setTitle("Test Case for Encounter");
        testCase.setChiefComplaint("Test complaint");
        testCase.setStatus(CaseStatus.OPEN);
        testCase.setPriority(CasePriority.MEDIUM);
        caseRepository.save(testCase);
    }

    @Test
    void createEncounter_withValidData_shouldPersist() {
        EncounterCreateRequest request = new EncounterCreateRequest();
        request.setCaseId(testCase.getId());
        request.setClinicianId(testClinician.getId());

        Encounter encounter = encounterService.createEncounter(request);

        assertThat(encounter).isNotNull();
        assertThat(encounter.getId()).isNotNull();
        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.DRAFT);
        assertThat(encounter.getPatientCase().getId()).isEqualTo(testCase.getId());
        assertThat(encounter.getClinician().getId()).isEqualTo(testClinician.getId());
    }

    @Test
    void createEncounter_shouldUpdateCaseStatusToInProgress() {
        EncounterCreateRequest request = new EncounterCreateRequest();
        request.setCaseId(testCase.getId());
        request.setClinicianId(testClinician.getId());

        encounterService.createEncounter(request);

        PatientCase updatedCase = caseRepository.findById(testCase.getId()).orElseThrow();
        assertThat(updatedCase.getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
    }

    @Test
    void createEncounter_withInvalidCaseId_shouldThrowException() {
        EncounterCreateRequest request = new EncounterCreateRequest();
        request.setCaseId(999999L);
        request.setClinicianId(testClinician.getId());

        assertThatThrownBy(() -> encounterService.createEncounter(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_withNonexistentId_shouldThrowException() {
        assertThatThrownBy(() -> encounterService.findById(999999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByCaseId_shouldReturnEncountersForCase() {
        EncounterCreateRequest request = new EncounterCreateRequest();
        request.setCaseId(testCase.getId());
        request.setClinicianId(testClinician.getId());
        encounterService.createEncounter(request);

        assertThat(encounterService.findByCaseId(testCase.getId())).isNotEmpty();
    }
}
