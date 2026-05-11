package com.aegispay.ledger.controller;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.ledger.domain.dto.AccountResponse;
import com.aegispay.ledger.domain.dto.LedgerEntryResponse;
import com.aegispay.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    /** Customer: get own account balances from JWT sub claim. */
    @GetMapping("/accounts/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(ledgerService.getAccountsForUser(userId)));
    }

    /** Back-office: get any user's accounts by userId. */
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
