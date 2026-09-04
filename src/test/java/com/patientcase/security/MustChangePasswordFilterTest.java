package com.patientcase.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.patientcase.user.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MustChangePasswordFilterTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /**
     * When mustChangePassword=false, normal navigation is allowed.
     */
    @Test
    @Transactional
    void normalUser_canAccessDashboard() throws Exception {
        User u = createUser("filter.normal", false);
        mockMvc.perform(get("/dashboard").with(user(u.getUsername()).roles("DOCTOR")))
                .andExpect(status().isOk());
    }

    /**
     * When mustChangePassword=true, any non-allowed page redirects to change-password.
     */
    @Test
    @Transactional
    void mustChangeUser_dashboardRedirectsToChangePassword() throws Exception {
        User u = createUser("filter.mustchange", true);
        mockMvc.perform(get("/dashboard").with(user(u.getUsername()).roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile/change-password?forced"));
    }

    /**
     * Even with mustChangePassword=true, the change-password page itself is accessible.
     */
    @Test
    @Transactional
    void mustChangeUser_canAccessChangePasswordPage() throws Exception {
        User u = createUser("filter.mustchange2", true);
        mockMvc.perform(get("/profile/change-password").with(user(u.getUsername()).roles("DOCTOR")))
                .andExpect(status().isOk());
    }

    /**
     * Static resources are not blocked even with mustChangePassword=true.
     * The filter must pass through /css/ paths — exact HTTP status depends on
     * whether the file exists (200 if present, 404 if absent). Either is correct;
     * a 302 redirect would indicate the filter is incorrectly blocking the resource.
     */
    @Test
    @Transactional
    void mustChangeUser_staticResourcesNotBlocked() throws Exception {
        User u = createUser("filter.mustchange3", true);
        int status = mockMvc.perform(get("/css/app.css").with(user(u.getUsername()).roles("DOCTOR")))
                .andReturn().getResponse().getStatus();
        // Must not be a redirect — 2xx (file exists) or 4xx (file absent) are both correct
        org.assertj.core.api.Assertions.assertThat(status)
                .as("Static resource must not be redirected by MustChangePasswordFilter")
                .isNotIn(301, 302, 303, 307, 308);
    }

    /**
     * Admin reset-password endpoint is accessible to ADMIN regardless of mustChangePassword.
     */
    @Test
    @Transactional
    void adminUser_resetPasswordEndpoint_accessible() throws Exception {
        User admin = createUser("filter.admin", false);
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        User target = createUser("filter.target", false);

        mockMvc.perform(get("/admin/users/" + target.getId() + "/reset-password")
                .with(user(admin.getUsername()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/reset-password"));
    }

    private User createUser(String username, boolean mustChange) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPasswordHash(passwordEncoder.encode("Password@1"));
        u.setFirstName("Filter"); u.setLastName("Test");
        u.setRole(Role.DOCTOR); u.setEnabled(true);
        u.setMustChangePassword(mustChange);
        return userRepository.save(u);
    }
}
