package com.patientcase.user;

import com.patientcase.common.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceAdminTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = new User();
        existingUser.setUsername("existing.admin.user");
        existingUser.setEmail("existing.admin@test.com");
        existingUser.setPasswordHash(passwordEncoder.encode("Password@1"));
        existingUser.setFirstName("Existing");
        existingUser.setLastName("User");
        existingUser.setRole(Role.DOCTOR);
        existingUser.setEnabled(true);
        userRepository.save(existingUser);
    }

    // --- createUser ---

    @Test
    void createUser_withValidData_savesUserWithBcryptHash() {
        UserCreateRequest req = buildCreateRequest("new.doctor", "new.doctor@test.com", Role.DOCTOR);

        User created = userService.createUser(req);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getUsername()).isEqualTo("new.doctor");
        assertThat(created.getRole()).isEqualTo(Role.DOCTOR);
        assertThat(created.isEnabled()).isTrue();
        // Password must be stored as BCrypt hash, never plaintext
        assertThat(created.getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches("Password@1", created.getPasswordHash())).isTrue();
    }

    @Test
    void createUser_duplicateUsername_throws() {
        UserCreateRequest req = buildCreateRequest(existingUser.getUsername(), "other@test.com", Role.NURSE);

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists");
    }

    @Test
    void createUser_duplicateEmail_throws() {
        UserCreateRequest req = buildCreateRequest("unique.username", existingUser.getEmail(), Role.NURSE);

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void createUser_passwordMismatch_throws() {
        UserCreateRequest req = buildCreateRequest("another.user", "another@test.com", Role.NURSE);
        req.setConfirmPassword("DifferentPass@9");

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not match");
    }

    // --- updateUser ---

    @Test
    void updateUser_changesNameAndRole() {
        UserEditRequest req = new UserEditRequest();
        req.setEmail(existingUser.getEmail());
        req.setFirstName("Updated");
        req.setLastName("Name");
        req.setRole(Role.NURSE);

        User updated = userService.updateUser(existingUser.getId(), req);

        assertThat(updated.getFirstName()).isEqualTo("Updated");
        assertThat(updated.getRole()).isEqualTo(Role.NURSE);
    }

    @Test
    void updateUser_emailTakenByOther_throws() {
        // Create a second user to take the email
        User other = new User();
        other.setUsername("other.user2");
        other.setEmail("taken@test.com");
        other.setPasswordHash(passwordEncoder.encode("x"));
        other.setFirstName("A"); other.setLastName("B");
        other.setRole(Role.RECEPTIONIST); other.setEnabled(true);
        userRepository.save(other);

        UserEditRequest req = new UserEditRequest();
        req.setEmail("taken@test.com");
        req.setFirstName("X"); req.setLastName("Y");
        req.setRole(Role.DOCTOR);

        assertThatThrownBy(() -> userService.updateUser(existingUser.getId(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already in use");
    }

    // --- setEnabled ---

    @Test
    void setEnabled_false_disablesUser() {
        userService.setEnabled(existingUser.getId(), false, "some.other.admin");

        User updated = userRepository.findById(existingUser.getId()).orElseThrow();
        assertThat(updated.isEnabled()).isFalse();
    }

    @Test
    void setEnabled_selfDisable_throws() {
        assertThatThrownBy(() ->
                userService.setEnabled(existingUser.getId(), false, existingUser.getUsername()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot disable your own account");
    }

    @Test
    void setEnabled_nonExistentUser_throws() {
        assertThatThrownBy(() -> userService.setEnabled(999999L, false, "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- findAllClinicians ---

    @Test
    void findAllClinicians_returnsOnlyDoctorAndNurse() {
        // Add an ADMIN and RECEPTIONIST — they must not appear
        User admin = new User();
        admin.setUsername("test.admin2"); admin.setEmail("tadmin2@test.com");
        admin.setPasswordHash("x"); admin.setFirstName("A"); admin.setLastName("Admin");
        admin.setRole(Role.ADMIN); admin.setEnabled(true);
        userRepository.save(admin);

        User recep = new User();
        recep.setUsername("test.recep2"); recep.setEmail("trecep2@test.com");
        recep.setPasswordHash("x"); recep.setFirstName("R"); recep.setLastName("Recep");
        recep.setRole(Role.RECEPTIONIST); recep.setEnabled(true);
        userRepository.save(recep);

        var clinicians = userService.findAllClinicians();
        clinicians.forEach(u ->
            assertThat(u.getRole()).isIn(Role.DOCTOR, Role.NURSE)
        );
    }

    // --- Helper ---

    private UserCreateRequest buildCreateRequest(String username, String email, Role role) {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername(username);
        req.setEmail(email);
        req.setFirstName("Test");
        req.setLastName("User");
        req.setRole(role);
        req.setPassword("Password@1");
        req.setConfirmPassword("Password@1");
        return req;
    }
}
