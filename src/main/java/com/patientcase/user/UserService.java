package com.patientcase.user;

import com.patientcase.common.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<User> findAllClinicians() {
        return userRepository.findByRoleAndEnabledTrue(Role.DOCTOR);
    }

    @Transactional(readOnly = true)
    public List<User> findAllActiveUsers() {
        return userRepository.findByEnabledTrue();
    }

    /**
     * Changes the authenticated user's password.
     * Requires the current password to be correct.
     * Passwords are never logged.
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = findByUsername(username);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New passwords do not match.");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must differ from the current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
