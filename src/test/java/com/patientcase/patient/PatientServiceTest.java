package com.patientcase.patient;

import com.patientcase.audit.AuditLogRepository;
import com.patientcase.audit.AuditService;
import com.patientcase.common.ResourceNotFoundException;
import com.patientcase.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PatientServiceTest {

    @Autowired
    private PatientService patientService;

    @Autowired
    private PatientRepository patientRepository;

    private PatientCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        createRequest = new PatientCreateRequest();
        createRequest.setFirstName("Jane");
        createRequest.setLastName("Doe");
        createRequest.setDateOfBirth(LocalDate.of(1990, 5, 15));
        createRequest.setGender(Gender.FEMALE);
        createRequest.setPhone("555-9999");
        createRequest.setEmail("jane.doe@test.com");
    }

    @Test
    void createPatient_withValidData_shouldPersist() {
        Patient patient = patientService.createPatient(createRequest);

        assertThat(patient).isNotNull();
        assertThat(patient.getId()).isNotNull();
        assertThat(patient.getFirstName()).isEqualTo("Jane");
        assertThat(patient.getLastName()).isEqualTo("Doe");
        assertThat(patient.getPatientNumber()).startsWith("P-");
        assertThat(patient.getGender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    void createPatient_patientNumberShouldBeUnique() {
        Patient patient1 = patientService.createPatient(createRequest);

        PatientCreateRequest request2 = new PatientCreateRequest();
        request2.setFirstName("John");
        request2.setLastName("Smith");
        request2.setDateOfBirth(LocalDate.of(1985, 3, 10));
        request2.setGender(Gender.MALE);

        Patient patient2 = patientService.createPatient(request2);

        assertThat(patient1.getPatientNumber()).isNotEqualTo(patient2.getPatientNumber());
    }

    @Test
    void findById_withExistingId_shouldReturnPatient() {
        Patient saved = patientService.createPatient(createRequest);
        Patient found = patientService.findById(saved.getId());

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getFirstName()).isEqualTo("Jane");
    }

    @Test
    void findById_withNonexistentId_shouldThrowException() {
        assertThatThrownBy(() -> patientService.findById(999999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchPatients_byFirstName_shouldReturnResults() {
        patientService.createPatient(createRequest);

        Page<Patient> results = patientService.searchPatients("Jane", PageRequest.of(0, 10));
        assertThat(results.getTotalElements()).isGreaterThan(0);
        assertThat(results.getContent()).anyMatch(p -> p.getFirstName().equals("Jane"));
    }

    @Test
    void updatePatient_shouldUpdateFields() {
        Patient saved = patientService.createPatient(createRequest);

        PatientUpdateRequest updateRequest = new PatientUpdateRequest();
        updateRequest.setFirstName("Janet");
        updateRequest.setLastName("Doe");
        updateRequest.setDateOfBirth(LocalDate.of(1990, 5, 15));
        updateRequest.setGender(Gender.FEMALE);
        updateRequest.setAllergies("Penicillin");

        Patient updated = patientService.updatePatient(saved.getId(), updateRequest);

        assertThat(updated.getFirstName()).isEqualTo("Janet");
        assertThat(updated.getAllergies()).isEqualTo("Penicillin");
    }

    @Test
    void searchPatients_emptySearch_shouldReturnAll() {
        patientService.createPatient(createRequest);

        Page<Patient> results = patientService.searchPatients("", PageRequest.of(0, 100));
        assertThat(results.getTotalElements()).isGreaterThan(0);
    }
}
