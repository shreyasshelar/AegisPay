package com.aegispay.ledger.controller;

import com.aegispay.ledger.domain.dto.AccountResponse;
import com.aegispay.ledger.domain.dto.LedgerEntryResponse;
import com.aegispay.ledger.service.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LedgerController.class)
class LedgerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    LedgerService ledgerService;

    UUID userId = UUID.randomUUID();
    UUID txnId = UUID.randomUUID();

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void getAccountsForUser_returns_accounts() throws Exception {
        AccountResponse account = AccountResponse.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .currency("USD")
                .availableBalance(new BigDecimal("100.00"))
                .reservedBalance(BigDecimal.ZERO)
                .totalBalance(new BigDecimal("100.00"))
                .updatedAt(Instant.now())
                .build();

        when(ledgerService.getAccountsForUser(userId)).thenReturn(List.of(account));

        mockMvc.perform(get("/api/v1/ledger/accounts/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].currency").value("USD"))
                .andExpect(jsonPath("$.data[0].availableBalance").value(100.00));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getAccountsForUser_customer_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/ledger/accounts/{userId}", userId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getEntriesForTransaction_returns_entries() throws Exception {
        LedgerEntryResponse entry = LedgerEntryResponse.builder()
                .id(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .transactionId(txnId)
                .entryType("RESERVE")
                .amount(new BigDecimal("50.00"))
                .balanceBefore(new BigDecimal("100.00"))
                .balanceAfter(new BigDecimal("50.00"))
                .createdAt(Instant.now())
                .build();

        when(ledgerService.getEntriesForTransaction(txnId)).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/v1/ledger/entries").param("transactionId", txnId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].entryType").value("RESERVE"))
                .andExpect(jsonPath("$.data[0].amount").value(50.00));
    }
}
