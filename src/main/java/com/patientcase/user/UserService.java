package com.patientcase.user;

import com.patientcase.common.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
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
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Returns active DOCTOR and NURSE users — the only roles that may be
     * assigned as appointment clinicians.
     */
    @Transactional(readOnly = true)
    public List<User> findAllClinicians() {
        List<User> clinicians = new java.util.ArrayList<>(userRepository.findByRoleAndEnabledTrue(Role.DOCTOR));
        clinicians.addAll(userRepository.findByRoleAndEnabledTrue(Role.NURSE));
        clinicians.sort(java.util.Comparator.comparing(User::getLastName));
        return clinicians;
    }

    @Transactional(readOnly = true)
    public List<User> findAllActiveUsers() {
        return userRepository.findByEnabledTrue();
    }

    /**
     * Creates a new user. Passwords are BCrypt-hashed; never stored or logged as plaintext.
     */
    @Transactional
    public User createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use: " + request.getEmail());
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(request.getRole());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(true);
        return userRepository.save(user);
    }

    /**
     * Updates non-security fields (email, name, role).
     * Username and password are not changed here.
     */
    @Transactional
    public User updateUser(Long id, UserEditRequest request) {
        User user = findById(id);
        if (!user.getEmail().equalsIgnoreCase(request.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use: " + request.getEmail());
        }
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(request.getRole());
        return userRepository.save(user);
    }

    /**
     * Enables or disables a user account.
     * An admin may not disable their own account to prevent lockout.
     */
    @Transactional
    public User setEnabled(Long id, boolean enabled, String requestingUsername) {
        User user = findById(id);
        if (!enabled && user.getUsername().equals(requestingUsername)) {
            throw new IllegalArgumentException("You cannot disable your own account.");
        }
        user.setEnabled(enabled);
        return userRepository.save(user);
    }

    /**
     * Admin-initiated password reset.
     * Generates a cryptographically random 16-character temporary password,
     * BCrypt-hashes it, sets mustChangePassword=true, and returns the plaintext
     * to the caller for one-time display only.
     *
     * Security guarantees:
     * - Plaintext is NEVER logged, never stored, and never placed in audit metadata.
     * - An admin cannot use this method on their own account.
     */
    @Transactional
    public String resetPassword(Long targetUserId, String requestingAdminUsername) {
        User target = findById(targetUserId);
        if (target.getUsername().equals(requestingAdminUsername)) {
            throw new IllegalArgumentException(
                    "Use the Change Password page to change your own password.");
        }
        String tempPassword = generateTemporaryPassword();
        target.setPasswordHash(passwordEncoder.encode(tempPassword));
        target.setMustChangePassword(true);
        userRepository.save(target);
        return tempPassword;
    }

    /**
     * Generates a 16-character cryptographically random password.
     * Contains at least one uppercase, lowercase, digit, and symbol.
     */
    private String generateTemporaryPassword() {
        final String UPPER   = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String LOWER   = "abcdefghjkmnpqrstuvwxyz";
        final String DIGITS  = "23456789";
        final String SYMBOLS = "!@#%&*";
        final String ALL     = UPPER + LOWER + DIGITS + SYMBOLS;
        SecureRandom rnd = new SecureRandom();
        char[] pw = new char[16];
        pw[0] = UPPER.charAt(rnd.nextInt(UPPER.length()));
        pw[1] = LOWER.charAt(rnd.nextInt(LOWER.length()));
        pw[2] = DIGITS.charAt(rnd.nextInt(DIGITS.length()));
        pw[3] = SYMBOLS.charAt(rnd.nextInt(SYMBOLS.length()));
        for (int i = 4; i < 16; i++) {
            pw[i] = ALL.charAt(rnd.nextInt(ALL.length()));
        }
        // Shuffle so guaranteed characters don't occupy predictable positions
        for (int i = 15; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            char tmp = pw[i]; pw[i] = pw[j]; pw[j] = tmp;
        }
        return new String(pw);
    }

    /**
     * Clears the mustChangePassword flag after a successful voluntary password change.
     * Called by ProfileController after changePassword succeeds.
     */
    @Transactional
    public void clearMustChangePassword(String username) {
        User user = findByUsername(username);
        if (user.isMustChangePassword()) {
            user.setMustChangePassword(false);
            userRepository.save(user);
        }
    }

    /**
     * Changes the authenticated user's own password.
     * Requires the current password to be correct. Never logs passwords.
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
