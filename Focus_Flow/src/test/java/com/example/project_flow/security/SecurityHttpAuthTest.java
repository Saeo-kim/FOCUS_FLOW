package com.example.project_flow.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-level checks: administration needs a session, and the Owner's Office read needs its token. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "focusflow.security-integration-token=security-integration-test-token-0123456789")
class SecurityHttpAuthTest {

    static final String TOKEN = "security-integration-test-token-0123456789";
    static final String SUMMARY = "/api/integration/owners-office/security/summary";

    @Autowired MockMvc mvc;

    @Test
    void administrationRequiresASignedInSession() throws Exception {
        mvc.perform(get("/api/admin/security/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/security/items")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/security/log/verify")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/security/grants").header("X-FocusFlow", "1").contentType("application/json")
                .content("{\"actorUserId\":1,\"maxLevel\":\"INTERNAL\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void theIntegrationRequiresItsTokenAndAnswersReadOnly() throws Exception {
        mvc.perform(get(SUMMARY)).andExpect(status().isUnauthorized());
        mvc.perform(get(SUMMARY).header("Authorization", "Bearer wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get(SUMMARY).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logIntegrity.intact").value(true))
                .andExpect(jsonPath("$.securityExpenses").exists());
        mvc.perform(post(SUMMARY).header("Authorization", "Bearer " + TOKEN).header("X-FocusFlow", "1")
                .contentType("application/json").content("{}")).andExpect(status().is4xxClientError());
    }

    @Test
    void theIntegrationTokenDoesNotOpenAdministration() throws Exception {
        mvc.perform(get("/api/admin/security/summary").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized());
    }
}
