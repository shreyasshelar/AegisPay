package com.aegispay.transaction.controller;

import com.aegispay.common.domain.enums.TransactionStatus;
import com.aegispay.transaction.config.SecurityConfig;
import com.aegispay.transaction.domain.dto.TransactionRequest;
import com.aegispay.transaction.domain.dto.TransactionResponse;
import com.aegispay.transaction.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
class TransactionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private TransactionService transactionService;
    // Provide a JwtDecoder mock so Spring Security can initialise the resource-server
    // filter chain in the @WebMvcTest slice (no issuer-uri available in test context).
    @MockBean  private JwtDecoder jwtDecoder;

    private static final UUID TXN_ID   = UUID.randomUUID();
    private static final UUID USER_ID  = UUID.randomUUID();
    private static final UUID PAYER_ID = UUID.randomUUID();
    private static final UUID PAYEE_ID = UUID.randomUUID();

    @Test
    void createReturns201() throws Exception {
        TransactionRequest request = new TransactionRequest(
                PAYEE_ID, new BigDecimal("1000.00"), "INR", null, null);

        when(transactionService.create(any(), eq("idem-001"), any(UUID.class)))
                .thenReturn(buildResponse());

        mockMvc.perform(post("/api/v1/transactions")
                        .with(jwt().jwt(j -> j
                                .subject(USER_ID.toString())
                                .claim("aegispay_user_id", USER_ID.toString())))
                        .header("X-Idempotency-Key", "idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("INITIATED"));
    }

    @Test
    void createReturns401WhenUnauthenticated() throws Exception {
        TransactionRequest request = new TransactionRequest(
                PAYEE_ID, new BigDecimal("1000.00"), "INR", null, null);

        mockMvc.perform(post("/api/v1/transactions")
                        .header("X-Idempotency-Key", "idem-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReturns400OnInvalidAmount() throws Exception {
        String badRequest = "{\"payerId\":\"" + PAYER_ID + "\",\"payeeId\":\"" + PAYEE_ID
                + "\",\"amount\":\"-1.00\",\"currency\":\"INR\"}";

        mockMvc.perform(post("/api/v1/transactions")
                        .with(jwt().jwt(j -> j.subject(USER_ID.toString())))
                        .header("X-Idempotency-Key", "idem-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void getByIdReturns200() throws Exception {
        when(transactionService.getById(TXN_ID)).thenReturn(buildResponse());

        mockMvc.perform(get("/api/v1/transactions/" + TXN_ID)
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INITIATED"));
    }

    private TransactionResponse buildResponse() {
        return TransactionResponse.builder()
                .id(TXN_ID)
                .userId(USER_ID)
                .payerId(PAYER_ID)
                .payeeId(PAYEE_ID)
                .amount(new BigDecimal("1000.00"))
                .currency("INR")
                .status(TransactionStatus.INITIATED)
                .idempotencyKey("idem-001")
                .initiatedAt(Instant.now())
                .build();
    }
}
