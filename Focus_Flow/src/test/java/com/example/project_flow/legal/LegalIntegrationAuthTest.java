package com.example.project_flow.legal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The integration is closed when no token is configured, and the session-check exemption covers only
 * the integration handlers. These run with the token UNSET, the default in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "focusflow.legal-integration-token=")
class LegalIntegrationAuthTest {

    @Autowired MockMvc mvc;

    private static final String REVIEWS = "/api/integration/owners-office/legal/reviews";

    @Test
    void unconfiguredTokenRefusesEveryRequestWith401() throws Exception {
        mvc.perform(get(REVIEWS)).andExpect(status().isUnauthorized());
        mvc.perform(get(REVIEWS).header("Authorization", "Bearer ")).andExpect(status().isUnauthorized());
        mvc.perform(get(REVIEWS).header("Authorization", "Bearer anything-at-all")).andExpect(status().isUnauthorized());
        mvc.perform(get(REVIEWS + "/1").header("Authorization", "Bearer anything-at-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void exemptionDoesNotLeakToAdministrationEndpoints() throws Exception {
        mvc.perform(get("/api/admin/legal/positions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/legal/owner")).andExpect(status().isUnauthorized());
        // With the CSRF header, a signed-out POST reaches the session check and gets 401.
        // Without it, the existing CSRF gate answers 403 first; that gate is unchanged.
        mvc.perform(post("/api/admin/legal/reviews/1/human-decisions").header("X-FocusFlow", "1").contentType("application/json")
                .content("{\"outcome\":\"APPROVE\",\"reasoning\":\"x\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void traversalAttemptsThroughTheIntegrationPrefixDoNotReachAdminEndpoints() throws Exception {
        mvc.perform(get("/api/integration/owners-office/legal/../../../admin/legal/positions"))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/integration/../admin/legal/positions"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void integrationHasNoWriteEndpoints() throws Exception {
        mvc.perform(post(REVIEWS + "/1/human-decisions").contentType("application/json")
                .content("{\"outcome\":\"APPROVE\",\"reasoning\":\"x\"}")).andExpect(status().is4xxClientError());
        mvc.perform(post(REVIEWS).contentType("application/json").content("{}")).andExpect(status().is4xxClientError());
    }
}
