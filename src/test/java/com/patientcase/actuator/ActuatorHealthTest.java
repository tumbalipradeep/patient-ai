package com.patientcase.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorHealthTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpoint_unauthenticated_returnsOk() throws Exception {
        // /actuator/health is explicitly permitted without authentication — used by
        // Docker/K8s health probes. Must return 200 with status UP.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void actuatorRoot_unauthenticated_redirectsToLogin() throws Exception {
        // /actuator root is NOT in the permitted list. Spring Security correctly
        // redirects unauthenticated requests to login — this is the desired behavior.
        // It is NOT exposed as a public endpoint.
        mockMvc.perform(get("/actuator"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login**"));
    }

    @Test
    void actuatorInfo_unauthenticated_redirectsToLogin() throws Exception {
        // /actuator/info is not in the management.endpoints.web.exposure.include list.
        // Spring Security redirects unauthenticated requests before Actuator can respond.
        // This confirms the endpoint is not publicly accessible.
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login**"));
    }
}
