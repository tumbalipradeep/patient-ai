package com.patientcase.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedUser_shouldNotAccessDashboard() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void unauthenticatedUser_shouldNotAccessPatients() throws Exception {
        mockMvc.perform(get("/patients"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void authenticatedDoctor_shouldAccessDashboard() throws Exception {
        mockMvc.perform(get("/dashboard")
                .with(user("dr.test").roles("DOCTOR")))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedPatient_shouldNotAccessDashboard() throws Exception {
        mockMvc.perform(get("/dashboard")
                .with(user("kiosk.test").roles("PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedDoctor_shouldAccessPatients() throws Exception {
        mockMvc.perform(get("/patients")
                .with(user("dr.test").roles("DOCTOR")))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedReceptionist_shouldAccessPatients() throws Exception {
        mockMvc.perform(get("/patients")
                .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isOk());
    }

    @Test
    void loginPageShouldBePublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUser_requestingUnknownRoute_shouldGet404Not500() throws Exception {
        mockMvc.perform(get("/definitely-not-a-route")
                        .with(user("dr.test").roles("DOCTOR")))
                .andExpect(status().isNotFound())
                .andExpect(view().name("errors/404"));
    }

    @Test
    void anonymousUser_requestingUnknownRoute_shouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/definitely-not-a-route"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void authenticatedUser_gettingPostOnlyRoute_shouldGet405Not500() throws Exception {
        mockMvc.perform(get("/kiosk/intake/9999/submit")
                        .with(user("kiosk.test").roles("PATIENT")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(view().name("errors/405"));
    }

    @Test
    void documentUploadWithoutFile_shouldGet400Not500() throws Exception {
        mockMvc.perform(multipart("/documents/upload")
                        .with(csrf())
                        .with(user("dr.test").roles("DOCTOR")))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("errors/400"));
    }
}
