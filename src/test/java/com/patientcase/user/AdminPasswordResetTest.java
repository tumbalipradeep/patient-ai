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
class AdminPasswordResetTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User targetUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setUsername("reset.admin");
        adminUser.setEmail("reset.admin@test.com");
        adminUser.setPasswordHash(passwordEncoder.encode("AdminPass@1"));
        adminUser.setFirstName("Reset"); adminUser.setLastName("Admin");
        adminUser.setRole(Role.ADMIN); adminUser.setEnabled(true);
        userRepository.save(adminUser);

        targetUser = new User();
        targetUser.setUsername("reset.target");
        targetUser.setEmail("reset.target@test.com");
        targetUser.setPasswordHash(passwordEncoder.encode("OldPass@1"));
        targetUser.setFirstName("Target"); targetUser.setLastName("User");
        targetUser.setRole(Role.DOCTOR); targetUser.setEnabled(true);
        targetUser.setMustChangePassword(false);
        userRepository.save(targetUser);
    }

    @Test
    void resetPassword_returnsNonBlankTemporaryPassword() {
        String temp = userService.resetPassword(targetUser.getId(), adminUser.getUsername());

        assertThat(temp).isNotBlank();
        assertThat(temp.length()).isGreaterThanOrEqualTo(16);
    }

    @Test
    void resetPassword_storedPasswordIsBcryptHash() {
        String temp = userService.resetPassword(targetUser.getId(), adminUser.getUsername());

        User updated = userRepository.findById(targetUser.getId()).orElseThrow();
        // Hash must start with BCrypt prefix
        assertThat(updated.getPasswordHash()).startsWith("$2a$");
        // Plaintext is NOT stored
        assertThat(updated.getPasswordHash()).doesNotContain(temp);
        // But the hash must verify against the plaintext
        assertThat(passwordEncoder.matches(temp, updated.getPasswordHash())).isTrue();
    }

    @Test
    void resetPassword_setsMustChangePasswordTrue() {
        userService.resetPassword(targetUser.getId(), adminUser.getUsername());

        User updated = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(updated.isMustChangePassword()).isTrue();
    }

    @Test
    void resetPassword_oldPasswordNoLongerWorks() {
        userService.resetPassword(targetUser.getId(), adminUser.getUsername());

        User updated = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("OldPass@1", updated.getPasswordHash())).isFalse();
    }

    @Test
    void resetPassword_selfReset_throws() {
        // Admin cannot reset their own password via resetPassword
        assertThatThrownBy(() ->
                userService.resetPassword(adminUser.getId(), adminUser.getUsername()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Change Password");
    }

    @Test
    void resetPassword_temporaryPasswordsMeetMinimumLength() {
        // Generate several to confirm consistency
        for (int i = 0; i < 5; i++) {
            String temp = userService.resetPassword(targetUser.getId(), adminUser.getUsername());
            assertThat(temp.length()).isGreaterThanOrEqualTo(16);
        }
    }

    @Test
    void clearMustChangePassword_clearsFlagAfterSuccessfulChange() {
        userService.resetPassword(targetUser.getId(), adminUser.getUsername());
        // Simulate the user changing their password
        userService.clearMustChangePassword(targetUser.getUsername());

        User updated = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(updated.isMustChangePassword()).isFalse();
    }
}
