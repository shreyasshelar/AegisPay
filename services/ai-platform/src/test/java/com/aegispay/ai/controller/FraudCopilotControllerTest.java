package com.aegispay.ai.controller;

import com.aegispay.ai.config.SecurityConfig;
import com.aegispay.ai.fraud.FraudCopilotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FraudCopilotController.class)
@Import(SecurityConfig.class)
class FraudCopilotControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean FraudCopilotService fraudCopilotService;
    @MockBean org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void explain_returns_200_with_explanation() throws Exception {
        UUID txId = UUID.randomUUID();
        when(fraudCopilotService.explain(any(), anyInt(), anyList()))
                .thenReturn("Velocity spike from shared IP.");

        String body = objectMapper.writeValueAsString(Map.of(
                "transactionId", txId.toString(),
                "riskScore", 80,
                "flaggedRules", List.of("VELOCITY", "BLACKLIST")));

        mockMvc.perform(post("/api/v1/ai/fraud/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation").value("Velocity spike from shared IP."));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void explain_returns_403_for_customer_role() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "transactionId", UUID.randomUUID().toString(),
                "riskScore", 80,
                "flaggedRules", List.of("VELOCITY")));

        mockMvc.perform(post("/api/v1/ai/fraud/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void explain_returns_400_when_flagged_rules_empty() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "transactionId", UUID.randomUUID().toString(),
                "riskScore", 80,
                "flaggedRules", List.of()));

        mockMvc.perform(post("/api/v1/ai/fraud/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
