package com.patientcase.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void changePasswordPage_unauthenticated_shouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/profile/change-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void changePasswordPage_authenticated_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/profile/change-password")
                .with(user("dr.test").roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("profile/change-password"));
    }

    @Test
    void documentsPage_unauthenticated_shouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/documents"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void documentsPage_authenticated_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/documents")
                .with(user("dr.test").roles("DOCTOR")))
                .andExpect(status().isOk());
    }
}
