package com.patientcase.patient;

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
class PatientNumberGenerationTest {

    @Autowired
    private PatientService patientService;

    private PatientCreateRequest buildRequest(String first, String last) {
        PatientCreateRequest req = new PatientCreateRequest();
        req.setFirstName(first);
        req.setLastName(last);
        req.setDateOfBirth(LocalDate.of(1990, 1, 1));
        req.setGender(Gender.FEMALE);
        return req;
    }

    @Test
    void consecutivePatientCreations_shouldProduceUniqueNumbers() {
        Patient p1 = patientService.createPatient(buildRequest("Alice", "Smith"));
        Patient p2 = patientService.createPatient(buildRequest("Bob", "Jones"));
        Patient p3 = patientService.createPatient(buildRequest("Carol", "Brown"));

        assertThat(p1.getPatientNumber()).isNotEqualTo(p2.getPatientNumber());
        assertThat(p2.getPatientNumber()).isNotEqualTo(p3.getPatientNumber());
        assertThat(p1.getPatientNumber()).startsWith("P-");
        assertThat(p2.getPatientNumber()).startsWith("P-");
    }

    @Test
    void patientNumber_shouldFollowExpectedFormat() {
        Patient p = patientService.createPatient(buildRequest("Test", "Format"));
        // Expected format: P-NNNNNN (6 digits)
        assertThat(p.getPatientNumber()).matches("P-\\d{6}");
    }
}
