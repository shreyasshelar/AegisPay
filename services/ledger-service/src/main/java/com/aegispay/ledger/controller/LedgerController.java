package com.aegispay.ledger.controller;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.ledger.domain.dto.AccountResponse;
import com.aegispay.ledger.domain.dto.LedgerEntryResponse;
import com.aegispay.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    /** Get all accounts for a user (balance summary). */
    @GetMapping("/accounts/{userId}")
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN', 'MERCHANT_OPS')")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAccountsForUser(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(ledgerService.getAccountsForUser(userId)));
    }

    /** Get ledger entries for a specific transaction (audit trail). */
    @GetMapping("/entries")
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<LedgerEntryResponse>>> getEntriesForTransaction(
            @RequestParam UUID transactionId) {
        return ResponseEntity.ok(ApiResponse.ok(ledgerService.getEntriesForTransaction(transactionId)));
    }
}
