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
class AdminSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    // --- Audit log ---

    @Test
    void auditLog_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/audit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void auditLog_asDoctor_isForbidden() throws Exception {
        mockMvc.perform(get("/admin/audit").with(user("dr.test").roles("DOCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditLog_asNurse_isForbidden() throws Exception {
        mockMvc.perform(get("/admin/audit").with(user("nurse.test").roles("NURSE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditLog_asAdmin_isOk() throws Exception {
        mockMvc.perform(get("/admin/audit").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/audit"));
    }

    // --- User management ---

    @Test
    void userManagement_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void userManagement_asDoctor_isForbidden() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("dr.test").roles("DOCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userManagement_asReceptionist_isForbidden() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userManagement_asAdmin_isOk() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/list"));
    }

    @Test
    void newUserForm_asAdmin_isOk() throws Exception {
        mockMvc.perform(get("/admin/users/new").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/form"));
    }
}
