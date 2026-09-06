package com.patientcase.kiosk;

import com.patientcase.audit.AuditAction;
import com.patientcase.audit.AuditService;
import com.patientcase.patient.Gender;
import com.patientcase.patient.Patient;
import com.patientcase.patient.PatientRepository;
import com.patientcase.user.Role;
import com.patientcase.user.User;
import com.patientcase.user.UserRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Patient self-registration for the MediKiosk portal.
 *
 * Creates a PATIENT-role login account AND a matching clinical Patient record
 * (patient number generated from the same sequence used by staff-created
 * patients, so numbering stays globally unique). Both records are linked
 * through patients.user_id.
 *
 * Passwords are always BCrypt-hashed and never stored or logged as plaintext.
 */
@Service
public class PatientRegistrationService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public PatientRegistrationService(UserRepository userRepository,
                                      PatientRepository patientRepository,
                                      PasswordEncoder passwordEncoder,
                                      AuditService auditService,
                                      JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Patient register(RegistrationForm form, String ipAddress) {
        if (userRepository.existsByUsername(form.getUsername())) {
            throw new IllegalArgumentException("That username is already taken.");
        }
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new IllegalArgumentException("An account with that email already exists.");
        }
        patientRepository.findByPatientNumber(form.getPatientNumber() != null
                ? form.getPatientNumber() : "").ifPresent(p -> {
            if (p.getEmail() != null && p.getEmail().equalsIgnoreCase(form.getEmail())) {
                throw new IllegalArgumentException(
                        "A patient record with this email already exists. Please contact the reception desk.");
            }
        });

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        if (form.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }

        User user = new User();
        user.setUsername(form.getUsername().strip());
        user.setEmail(form.getEmail().strip());
        user.setFirstName(form.getFirstName().strip());
        user.setLastName(form.getLastName().strip());
        user.setRole(Role.PATIENT);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setEnabled(true);
        user.setMustChangePassword(false);
        User savedUser = userRepository.save(user);

        Patient patient = new Patient();
        patient.setPatientNumber(generatePatientNumber());
        patient.setUser(savedUser);
        patient.setFirstName(form.getFirstName().strip());
        patient.setLastName(form.getLastName().strip());
        patient.setDateOfBirth(form.getDateOfBirth());
        patient.setGender(form.getGender());
        patient.setPhone(form.getPhone() != null ? form.getPhone().strip() : null);
        patient.setEmail(form.getEmail().strip());
        patient.setAddress(form.getAddress());
        Patient saved = patientRepository.save(patient);

        auditService.log(AuditAction.PATIENT_CREATED, "Patient", saved.getId(),
                "Patient self-registered portal account " + saved.getPatientNumber(), ipAddress);
        return saved;
    }

    private String generatePatientNumber() {
        Long nextVal = jdbcTemplate.queryForObject("SELECT NEXTVAL('patient_number_seq')", Long.class);
        return "P-" + String.format("%06d", nextVal);
    }

    /** Self-registration form — validated at the controller boundary. */
    public static class RegistrationForm {
        @NotBlank @Size(max = 100)
        private String firstName;
        @NotBlank @Size(max = 100)
        private String lastName;
        @NotNull
        private LocalDate dateOfBirth;
        @NotNull
        private Gender gender;
        @Pattern(regexp = "^[0-9+\\-\\s]{10,15}$", message = "Enter a valid phone number")
        private String phone;
        @NotBlank @Email @Size(max = 255)
        private String email;
        @NotBlank @Size(min = 3, max = 50)
        private String username;
        @NotBlank @Size(min = 8, max = 72)
        private String password;
        @NotBlank
        private String confirmPassword;
        @Size(max = 500)
        private String address;
        @Size(max = 20)
        private String patientNumber;

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public LocalDate getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
        public Gender getGender() { return gender; }
        public void setGender(Gender gender) { this.gender = gender; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getPatientNumber() { return patientNumber; }
        public void setPatientNumber(String patientNumber) { this.patientNumber = patientNumber; }
    }
}