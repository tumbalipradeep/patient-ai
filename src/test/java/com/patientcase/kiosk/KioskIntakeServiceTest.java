package com.patientcase.kiosk;

import com.patientcase.case_management.CasePriority;
import com.patientcase.case_management.PatientCase;
import com.patientcase.case_management.PatientCaseRepository;
import com.patientcase.consent.ConsentService;
import com.patientcase.encounter.Encounter;
import com.patientcase.encounter.EncounterRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level integration tests for the MediKiosk intake lifecycle.
 *
 * Uses the real Spring context + H2 (profile "test"), real repositories,
 * and the real KioskIntakeService. No mocking.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KioskIntakeServiceTest {

    @Autowired private KioskIntakeService intakeService;
    @Autowired private PatientRegistrationService registrationService;
    @Autowired private ConsentService consentService;
    @Autowired private UserRepository userRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PatientCaseRepository caseRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private RedFlagRepository redFlagRepository;
    @Autowired private KioskIntakeRepository intakeRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User patientUser;
    private Patient patient;
    private User otherPatientUser;
    private Patient otherPatient;
    private User clinician;

    @BeforeEach
    void setUp() {
        patientUser = saveUser("kiosk.patient", Role.PATIENT);
        patient = savePatient("P-KIO-01", patientUser, "Kiosk", "Patient");
        otherPatientUser = saveUser("kiosk.other", Role.PATIENT);
        otherPatient = savePatient("P-KIO-02", otherPatientUser, "Other", "Patient");
        clinician = saveUser("kiosk.doctor", Role.DOCTOR);
    }

    @Test
    void getOrCreateActiveIntake_createsOnceAndReuses() {
        KioskIntake first = intakeService.getOrCreateActiveIntake(patient.getId(), patientUser.getId());
        assertThat(first.getStatus()).isEqualTo(KioskIntakeStatus.IN_PROGRESS);

        KioskIntake again = intakeService.getOrCreateActiveIntake(patient.getId(), patientUser.getId());
        assertThat(again.getId()).isEqualTo(first.getId());
    }

    @Test
    void requireOwnedIntake_otherPatientsIntake_isDenied() {
        KioskIntake intake = intakeService.getOrCreateActiveIntake(patient.getId(), patientUser.getId());

        assertThatThrownBy(() -> intakeService.requireOwnedIntake(intake.getId(), otherPatient.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void submit_withoutConsent_isRejected() {
        KioskIntake intake = intakeService.getOrCreateActiveIntake(patient.getId(), patientUser.getId());
        intakeService.saveDraft(intake.getId(), patient.getId(), validDraftJson());

        assertThatThrownBy(() -> intakeService.submit(intake.getId(), patient.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consent");
    }

    @Test
    void fullFlow_consentDraftSubmit() {
        KioskIntake intake = intakeService.getOrCreateActiveIntake(patient.getId(), patientUser.getId());

        var consent = consentService.grant(patient.getId(), patientUser.getId(),
                "patient_intake", "medikiosk-1.0", "127.0.0.1");
        intakeService.bindConsent(intake.getId(), patient.getId(), consent);

        intakeService.saveDraft(intake.getId(), patient.getId(), validDraftJson());
        KioskIntake withDraft = intakeRepository.findById(intake.getId()).orElseThrow();
        assertThat(withDraft.getStatus()).isEqualTo(KioskIntakeStatus.DRAFT_READY);
        assertThat(withDraft.getConsent()).isNotNull();

        // Red flags were persisted as rows
        assertThat(redFlagRepository.findByIntakeId(intake.getId())).isNotEmpty();

        intakeService.submit(intake.getId(), patient.getId());
        KioskIntake submitted = intakeRepository.findById(intake.getId()).orElseThrow();
        assertThat(submitted.getStatus()).isEqualTo(KioskIntakeStatus.SUBMITTED);
        assertThat(intakeService.findSubmissionsForReview()).hasSize(1);
        assertThat(intakeService.countPendingReview()).isEqualTo(1);
    }

    @Test
    void acceptIntake_createsCaseAndEncounterForClinician() {
        KioskIntake intake = intakeService.getOrCreateActiveIntake(patient.getId(), patientUser.getId());
        var consent = consentService.grant(patient.getId(), patientUser.getId(),
                "patient_intake", "medikiosk-1.0", "127.0.0.1");
        intakeService.bindConsent(intake.getId(), patient.getId(), consent);
        intakeService.saveDraft(intake.getId(), patient.getId(),
                "{\"chiefComplaint\":\"Breathlessness\",\"historyOfPresentIllness\":\"2 days\","
                + "\"symptoms\":[{\"name\":\"Dyspnea\",\"severity\":\"SEVERE\",\"duration\":\"2 days\"}],"
                + "\"redFlags\":[\"Patient reports difficulty breathing at rest\"]}",
                true);
        intakeService.submit(intake.getId(), patient.getId());

        Encounter encounter = intakeService.acceptIntake(intake.getId(),
                clinician.getUsername(), "Triage note");

        PatientCase patientCase = encounter.getPatientCase();
        assertThat(patientCase).isNotNull();
        assertThat(patientCase.getPatient().getId()).isEqualTo(patient.getId());
        assertThat(patientCase.getPriority()).isEqualTo(CasePriority.URGENT);
        assertThat(encounter.getClinician().getUsername()).isEqualTo(clinician.getUsername());
        assertThat(encounter.getChiefComplaint()).isEqualTo("Breathlessness");

        // Red flags transferred from intake to encounter
        assertThat(redFlagRepository.findByIntakeId(intake.getId())).isEmpty();
        assertThat(caseRepository.findById(patientCase.getId())).isPresent();

        KioskIntake accepted = intakeRepository.findById(intake.getId()).orElseThrow();
        assertThat(accepted.getStatus()).isEqualTo(KioskIntakeStatus.ACCEPTED);
        assertThat(accepted.getReviewedBy()).isEqualTo(clinician.getUsername());
        assertThat(accepted.getClinicianNotes()).isEqualTo("Triage note");
    }

    @Test
    void acceptIntake_withoutUrgentFlag_setsHighPriority() {
        KioskIntake intake = intakeService.getOrCreateActiveIntake(patient.getId(), patientUser.getId());
        var consent = consentService.grant(patient.getId(), patientUser.getId(),
                "patient_intake", "medikiosk-1.0", "127.0.0.1");
        intakeService.bindConsent(intake.getId(), patient.getId(), consent);
        intakeService.saveDraft(intake.getId(), patient.getId(),
                "{\"chiefComplaint\":\"Cough\",\"redFlags\":[\"Cough duration notes\"]}");
        intakeService.submit(intake.getId(), patient.getId());

        Encounter encounter = intakeService.acceptIntake(intake.getId(),
                clinician.getUsername(), null);
        assertThat(encounter.getPatientCase().getPriority()).isEqualTo(CasePriority.HIGH);
    }

    @Test
    void rejectIntake_marksRejected() {
        KioskIntake intake = intakeService.getOrCreateActiveIntake(patient.getId(), patientUser.getId());
        var consent = consentService.grant(patient.getId(), patientUser.getId(),
                "patient_intake", "medikiosk-1.0", "127.0.0.1");
        intakeService.bindConsent(intake.getId(), patient.getId(), consent);
        intakeService.saveDraft(intake.getId(), patient.getId(), validDraftJson());
        intakeService.submit(intake.getId(), patient.getId());

        intakeService.rejectIntake(intake.getId(), clinician.getUsername(), "Please come in");

        KioskIntake rejected = intakeRepository.findById(intake.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(KioskIntakeStatus.REJECTED);
        assertThat(rejected.getReviewedBy()).isEqualTo(clinician.getUsername());
    }

    @Test
    void patientRegistration_createsLinkedUserAndPatient() {
        PatientRegistrationService.RegistrationForm form = new PatientRegistrationService.RegistrationForm();
        form.setFirstName("New");
        form.setLastName("Patient");
        form.setDateOfBirth(LocalDate.of(1992, 3, 15));
        form.setGender(Gender.MALE);
        form.setPhone("9812345678");
        form.setEmail("new.patient@example.com");
        form.setUsername("new.patient");
        form.setPassword("StrongPass1");
        form.setConfirmPassword("StrongPass1");

        Patient saved = registrationService.register(form, "127.0.0.1");

        assertThat(saved.getPatientNumber()).startsWith("P-");
        assertThat(saved.getUser()).isNotNull();
        assertThat(saved.getUser().getRole()).isEqualTo(Role.PATIENT);
        assertThat(saved.getUser().isEnabled()).isTrue();
        assertThat(userRepository.findByUsername("new.patient")).isPresent();
        assertThat(patientRepository.findByUserId(saved.getUser().getId())).isPresent();
    }

    @Test
    void patientRegistration_duplicateEmail_isRejected() {
        PatientRegistrationService.RegistrationForm form = new PatientRegistrationService.RegistrationForm();
        form.setFirstName("New");
        form.setLastName("Patient");
        form.setDateOfBirth(LocalDate.of(1992, 3, 15));
        form.setGender(Gender.MALE);
        form.setPhone("9812345678");
        form.setEmail(patientUser.getEmail());
        form.setUsername("new.patient2");
        form.setPassword("StrongPass1");
        form.setConfirmPassword("StrongPass1");

        assertThatThrownBy(() -> registrationService.register(form, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String validDraftJson() {
        return "{\"chiefComplaint\":\"Headache\",\"historyOfPresentIllness\":\"Since yesterday\","
                + "\"symptoms\":[{\"name\":\"Headache\",\"severity\":\"MODERATE\",\"duration\":\"2 days\"}],"
                + "\"redFlags\":[\"Patient reports sudden severe headache\"]}";
    }

    private User saveUser(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPasswordHash(passwordEncoder.encode("Pass@1"));
        u.setFirstName("Kiosk"); u.setLastName("Test");
        u.setRole(role); u.setEnabled(true);
        return userRepository.save(u);
    }

    private Patient savePatient(String number, User user, String first, String last) {
        Patient p = new Patient();
        p.setPatientNumber(number);
        p.setUser(user);
        p.setFirstName(first); p.setLastName(last);
        p.setDateOfBirth(LocalDate.of(1990, 6, 15));
        p.setGender(Gender.FEMALE);
        p.setEmail(user.getEmail());
        return patientRepository.save(p);
    }
}