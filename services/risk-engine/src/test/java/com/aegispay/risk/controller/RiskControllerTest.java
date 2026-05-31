package com.aegispay.risk.controller;

import com.aegispay.common.domain.enums.RiskDecision;
import com.aegispay.risk.config.SecurityConfig;
import com.aegispay.risk.domain.entity.RiskCase;
import com.aegispay.risk.exception.RiskCaseNotFoundException;
import com.aegispay.risk.repository.FraudBlacklistRepository;
import com.aegispay.risk.repository.RiskCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskController.class)
@Import(SecurityConfig.class)
class RiskControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean RiskCaseRepository riskCaseRepository;
    @MockBean FraudBlacklistRepository fraudBlacklistRepository;
    // Provide a JwtDecoder mock so the resource-server filter chain initialises
    // correctly in the @WebMvcTest slice (no issuer-uri in test context).
    @MockBean JwtDecoder jwtDecoder;

    UUID txnId = UUID.randomUUID();

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void getCase_returns_risk_case() throws Exception {
        RiskCase rc = new RiskCase();
        rc.setId(UUID.randomUUID());
        rc.setTransactionId(txnId);
        rc.setUserId(UUID.randomUUID());
        rc.setRiskScore(15);
        rc.setDecision(RiskDecision.APPROVED);
        rc.setRuleFlags(List.of());
        rc.setCreatedAt(Instant.now());

        when(riskCaseRepository.findByTransactionId(txnId)).thenReturn(Optional.of(rc));

        mockMvc.perform(get("/api/v1/risk/cases/{transactionId}", txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskScore").value(15))
                .andExpect(jsonPath("$.data.decision").value("APPROVED"));
    }

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void getCase_returns_404_when_not_found() throws Exception {
        when(riskCaseRepository.findByTransactionId(txnId))
                .thenThrow(new RiskCaseNotFoundException(txnId));

        mockMvc.perform(get("/api/v1/risk/cases/{transactionId}", txnId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getCase_forbidden_for_customer() throws Exception {
        mockMvc.perform(get("/api/v1/risk/cases/{transactionId}", txnId))
                .andExpect(status().isForbidden());
    }
}
