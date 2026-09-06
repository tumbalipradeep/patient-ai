package com.patientcase.case_management;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Boundary cases for the case-create form. Guards against the previous bug
 * where an invalid patientId caused a redirect to "/patients/null" (500).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientCaseCreateValidationTest {

    @Autowired private MockMvc mockMvc;

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder casePost(String patientId) {
        return post("/cases/new")
                .with(user("dr.test").roles("DOCTOR"))
                .with(csrf())
                .param("patientId", patientId)
                .param("title", "Ab")
                .param("chiefComplaint", "Cough since two days");
    }

    @Test
    void blankPatientId_renderFormInline_withoutRedirectToNull() throws Exception {
        mockMvc.perform(casePost(""))
                .andExpect(status().isOk())
                .andExpect(view().name("cases/new"))
                .andExpect(model().attributeExists("caseForm"));
    }

    @Test
    void nonNumericPatientId_renderFormInline_withoutRedirectToNull() throws Exception {
        mockMvc.perform(casePost("abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("cases/new"))
                .andExpect(model().attributeExists("caseForm"));
    }

    @Test
    void invalidTitle_renderFormWithValidationErrors() throws Exception {
        mockMvc.perform(casePost("1"))
                .andExpect(status().isOk())
                .andExpect(view().name("cases/new"))
                .andExpect(model().attributeHasFieldErrors("caseForm", "title"));
    }

    @Test
    void shortChiefComplaint_renderFormWithValidationErrors() throws Exception {
        mockMvc.perform(post("/cases/new")
                        .with(user("dr.test").roles("DOCTOR"))
                        .with(csrf())
                        .param("patientId", "1")
                        .param("title", "Valid title")
                        .param("chiefComplaint", "Hi"))
                .andExpect(status().isOk())
                .andExpect(view().name("cases/new"))
                .andExpect(model().attributeHasFieldErrors("caseForm", "chiefComplaint"));
    }
}