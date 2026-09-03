package com.patientcase.case_management;

import com.patientcase.common.ResourceNotFoundException;
import com.patientcase.patient.Gender;
import com.patientcase.patient.Patient;
import com.patientcase.patient.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PatientCaseServiceTest {

    @Autowired
    private PatientCaseService caseService;

    @Autowired
    private PatientRepository patientRepository;

    private Patient testPatient;

    @BeforeEach
    void setUp() {
        testPatient = new Patient();
        testPatient.setPatientNumber("P-TEST-01");
        testPatient.setFirstName("Test");
        testPatient.setLastName("Patient");
        testPatient.setDateOfBirth(LocalDate.of(1990, 1, 1));
        testPatient.setGender(Gender.MALE);
        patientRepository.save(testPatient);
    }

    @Test
    void createCase_withValidData_shouldPersist() {
        CaseCreateRequest request = new CaseCreateRequest();
        request.setPatientId(testPatient.getId());
        request.setTitle("Test Case");
        request.setChiefComplaint("Patient has a headache");
        request.setStatus(CaseStatus.OPEN);
        request.setPriority(CasePriority.MEDIUM);

        PatientCase created = caseService.createCase(request);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getCaseNumber()).startsWith("C-");
        assertThat(created.getTitle()).isEqualTo("Test Case");
        assertThat(created.getPatient().getId()).isEqualTo(testPatient.getId());
    }

    @Test
    void createCase_withInvalidPatientId_shouldThrowException() {
        CaseCreateRequest request = new CaseCreateRequest();
        request.setPatientId(999999L);
        request.setTitle("Test");
        request.setChiefComplaint("Test complaint");

        assertThatThrownBy(() -> caseService.createCase(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_withExistingId_shouldReturnCase() {
        CaseCreateRequest request = new CaseCreateRequest();
        request.setPatientId(testPatient.getId());
        request.setTitle("Find Test");
        request.setChiefComplaint("Complaint for find test");
        request.setStatus(CaseStatus.OPEN);
        request.setPriority(CasePriority.LOW);

        PatientCase created = caseService.createCase(request);
        PatientCase found = caseService.findById(created.getId());

        assertThat(found.getId()).isEqualTo(created.getId());
    }

    @Test
    void updateCase_shouldUpdateFields() {
        CaseCreateRequest request = new CaseCreateRequest();
        request.setPatientId(testPatient.getId());
        request.setTitle("Original Title");
        request.setChiefComplaint("Original complaint");
        request.setStatus(CaseStatus.OPEN);
        request.setPriority(CasePriority.MEDIUM);

        PatientCase created = caseService.createCase(request);

        CaseUpdateRequest updateRequest = new CaseUpdateRequest();
        updateRequest.setTitle("Updated Title");
        updateRequest.setChiefComplaint("Updated complaint");
        updateRequest.setStatus(CaseStatus.IN_PROGRESS);
        updateRequest.setPriority(CasePriority.HIGH);

        PatientCase updated = caseService.updateCase(created.getId(), updateRequest);

        assertThat(updated.getTitle()).isEqualTo("Updated Title");
        assertThat(updated.getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
        assertThat(updated.getPriority()).isEqualTo(CasePriority.HIGH);
    }

    @Test
    void findByPatientId_shouldReturnCasesForPatient() {
        CaseCreateRequest request = new CaseCreateRequest();
        request.setPatientId(testPatient.getId());
        request.setTitle("Patient Case 1");
        request.setChiefComplaint("Some complaint");
        request.setStatus(CaseStatus.OPEN);
        request.setPriority(CasePriority.LOW);
        caseService.createCase(request);

        assertThat(caseService.findByPatientId(testPatient.getId())).isNotEmpty();
    }
}
