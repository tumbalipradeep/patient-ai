package com.patientcase.user;

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
class UserServicePasswordTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private static final String ORIGINAL_PASSWORD = "Original@123";

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUsername("pwd.test.user");
        testUser.setEmail("pwd.test@test.com");
        testUser.setPasswordHash(passwordEncoder.encode(ORIGINAL_PASSWORD));
        testUser.setFirstName("Pwd");
        testUser.setLastName("Tester");
        testUser.setRole(Role.DOCTOR);
        testUser.setEnabled(true);
        userRepository.save(testUser);
    }

    @Test
    void changePassword_withCorrectCurrentPassword_shouldSucceed() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword(ORIGINAL_PASSWORD);
        req.setNewPassword("NewPass@456");
        req.setConfirmPassword("NewPass@456");

        userService.changePassword(testUser.getUsername(), req);

        User updated = userRepository.findByUsername(testUser.getUsername()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass@456", updated.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(ORIGINAL_PASSWORD, updated.getPasswordHash())).isFalse();
    }

    @Test
    void changePassword_withWrongCurrentPassword_shouldThrow() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("WrongPassword!");
        req.setNewPassword("NewPass@456");
        req.setConfirmPassword("NewPass@456");

        assertThatThrownBy(() -> userService.changePassword(testUser.getUsername(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void changePassword_withMismatchedNewPasswords_shouldThrow() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword(ORIGINAL_PASSWORD);
        req.setNewPassword("NewPass@456");
        req.setConfirmPassword("DifferentPass@789");

        assertThatThrownBy(() -> userService.changePassword(testUser.getUsername(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not match");
    }

    @Test
    void changePassword_withSameAsCurrentPassword_shouldThrow() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword(ORIGINAL_PASSWORD);
        req.setNewPassword(ORIGINAL_PASSWORD);
        req.setConfirmPassword(ORIGINAL_PASSWORD);

        assertThatThrownBy(() -> userService.changePassword(testUser.getUsername(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must differ");
    }
}
