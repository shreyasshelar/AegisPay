package com.aegispay.ai.controller;

import com.aegispay.ai.config.SecurityConfig;
import com.aegispay.ai.error.ErrorResolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ErrorResolutionController.class)
@Import(SecurityConfig.class)
class ErrorResolutionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ErrorResolutionService errorResolutionService;
    @MockBean org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Test
    @WithMockUser
    void resolve_returns_resolution() throws Exception {
        when(errorResolutionService.resolve(anyString(), anyString()))
                .thenReturn(new ErrorResolutionService.ErrorResolutionResponse(
                        "INSUFFICIENT_FUNDS", "Ask the user to top up their account."));

        String body = objectMapper.writeValueAsString(Map.of(
                "errorCode", "INSUFFICIENT_FUNDS",
                "errorMessage", "Not enough balance"));

        mockMvc.perform(post("/api/v1/ai/errors/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.resolution").value("Ask the user to top up their account."));
    }

    @Test
    @WithMockUser
    void resolve_returns_400_when_error_code_missing() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("errorMessage", "some error"));

        mockMvc.perform(post("/api/v1/ai/errors/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
