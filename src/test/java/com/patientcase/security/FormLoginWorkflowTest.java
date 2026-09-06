package com.patientcase.security;

import com.patientcase.user.Role;
import com.patientcase.user.User;
import com.patientcase.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the REAL username/password form-login path against the database.
 *
 * The rest of the suite authenticates with SecurityMockMvc mock principals,
 * so no test covered the actual /login exchange (the flow the
 * V7__fix_demo_user_passwords migration repaired). These tests guard it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormLoginWorkflowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User saveUser(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@test.local");
        u.setPasswordHash(passwordEncoder.encode("DemoPass@1"));
        u.setFirstName("Login"); u.setLastName("Flow");
        u.setRole(role); u.setEnabled(true); u.setMustChangePassword(false);
        return userRepository.save(u);
    }

    @Test
    void reportAndClinicalRole_canSignInAndReachDashboard() throws Exception {
        saveUser("login.staff", Role.ADMIN);
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "login.staff")
                        .param("password", "DemoPass@1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void patientRole_canSignInAndIsRoutedToKioskHome() throws Exception {
        saveUser("login.patient", Role.PATIENT);
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "login.patient")
                        .param("password", "DemoPass@1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kiosk/home"));
    }

    @Test
    void wrongPassword_returnsToLoginWithError() throws Exception {
        saveUser("login.wrong", Role.DOCTOR);
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "login.wrong")
                        .param("password", "WrongPass@1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"));
    }

    @Test
    void disabledUser_cannotSignIn() throws Exception {
        User u = saveUser("login.disabled", Role.DOCTOR);
        u.setEnabled(false);
        userRepository.save(u);
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "login.disabled")
                        .param("password", "DemoPass@1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"));
    }

    @Test
    void loginPage_rendersWithCsrfToken() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("_csrf")));
    }
}