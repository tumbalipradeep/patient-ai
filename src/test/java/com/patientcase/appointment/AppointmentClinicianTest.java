package com.patientcase.appointment;

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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AppointmentClinicianTest {

    @Autowired private AppointmentService appointmentService;
    @Autowired private PatientRepository patientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Patient patient;

    @BeforeEach
    void setUp() {
        patient = new Patient();
        patient.setPatientNumber("P-APPT-01");
        patient.setFirstName("Appt");
        patient.setLastName("Patient");
        patient.setDateOfBirth(LocalDate.of(1980, 1, 1));
        patient.setGender(Gender.FEMALE);
        patientRepository.save(patient);
    }

    @Test
    void createAppointment_withDoctor_succeeds() {
        User doctor = createUser("appt.doctor", Role.DOCTOR);
        AppointmentCreateRequest req = buildRequest(patient.getId(), doctor.getId());

        Appointment appt = appointmentService.createAppointment(req);
        assertThat(appt.getId()).isNotNull();
        assertThat(appt.getClinician().getRole()).isEqualTo(Role.DOCTOR);
    }

    @Test
    void createAppointment_withNurse_succeeds() {
        User nurse = createUser("appt.nurse", Role.NURSE);
        AppointmentCreateRequest req = buildRequest(patient.getId(), nurse.getId());

        Appointment appt = appointmentService.createAppointment(req);
        assertThat(appt.getId()).isNotNull();
        assertThat(appt.getClinician().getRole()).isEqualTo(Role.NURSE);
    }

    @Test
    void createAppointment_withAdmin_throws() {
        User admin = createUser("appt.admin", Role.ADMIN);
        AppointmentCreateRequest req = buildRequest(patient.getId(), admin.getId());

        assertThatThrownBy(() -> appointmentService.createAppointment(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DOCTOR or NURSE");
    }

    @Test
    void createAppointment_withReceptionist_throws() {
        User recep = createUser("appt.recep", Role.RECEPTIONIST);
        AppointmentCreateRequest req = buildRequest(patient.getId(), recep.getId());

        assertThatThrownBy(() -> appointmentService.createAppointment(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DOCTOR or NURSE");
    }

    // --- Helpers ---

    private User createUser(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPasswordHash(passwordEncoder.encode("x"));
        u.setFirstName("Test"); u.setLastName("User");
        u.setRole(role); u.setEnabled(true);
        return userRepository.save(u);
    }

    private AppointmentCreateRequest buildRequest(Long patientId, Long clinicianId) {
        AppointmentCreateRequest req = new AppointmentCreateRequest();
        req.setPatientId(patientId);
        req.setClinicianId(clinicianId);
        req.setAppointmentDatetime(LocalDateTime.now().plusDays(1));
        req.setReason("Test appointment");
        return req;
    }
}
