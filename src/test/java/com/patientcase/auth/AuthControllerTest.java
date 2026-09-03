package com.patientcase.auth;

import com.patientcase.user.Role;
import com.patientcase.user.User;
import com.patientcase.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUsername("testdoctor");
        testUser.setEmail("testdoctor@test.com");
        testUser.setPasswordHash(passwordEncoder.encode("TestPass@123"));
        testUser.setFirstName("Test");
        testUser.setLastName("Doctor");
        testUser.setRole(Role.DOCTOR);
        testUser.setEnabled(true);
        userRepository.save(testUser);
    }

    @Test
    void loginPage_shouldBeAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"));
    }

    @Test
    void login_withValidCredentials_shouldAuthenticate() throws Exception {
        mockMvc.perform(formLogin("/login")
                .user("username", "testdoctor")
                .password("password", "TestPass@123"))
                .andExpect(authenticated());
    }

    @Test
    void login_withInvalidPassword_shouldNotAuthenticate() throws Exception {
        mockMvc.perform(formLogin("/login")
                .user("username", "testdoctor")
                .password("password", "WrongPassword"))
                .andExpect(unauthenticated());
    }

    @Test
    void login_withNonexistentUser_shouldNotAuthenticate() throws Exception {
        mockMvc.perform(formLogin("/login")
                .user("username", "nonexistent")
                .password("password", "AnyPassword"))
                .andExpect(unauthenticated());
    }

    @Test
    void protectedDashboard_withoutAuthentication_shouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void loginPage_withLogoutParam_shouldShowLogoutMessage() throws Exception {
        mockMvc.perform(get("/login").param("logout", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("logoutMessage"));
    }

    @Test
    void loginPage_withErrorParam_shouldShowErrorMessage() throws Exception {
        mockMvc.perform(get("/login").param("error", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"));
    }
}
