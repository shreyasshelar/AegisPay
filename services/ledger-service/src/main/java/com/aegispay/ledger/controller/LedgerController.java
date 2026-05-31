package com.aegispay.ledger.controller;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.ledger.client.UserServiceClient;
import com.aegispay.ledger.domain.dto.AccountResponse;
import com.aegispay.ledger.domain.dto.LedgerEntryResponse;
import com.aegispay.ledger.domain.dto.TopUpConfirmRequest;
import com.aegispay.ledger.domain.dto.TopUpConfirmResponse;
import com.aegispay.ledger.domain.dto.TopUpIntentRequest;
import com.aegispay.ledger.domain.dto.TopUpIntentResponse;
import com.aegispay.ledger.service.LedgerService;
import com.aegispay.ledger.service.TopUpService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the ledger — account balances, entry audit trail, and wallet top-up.
 *
 * <h3>User ID resolution</h3>
 * All customer-facing endpoints derive the caller's AegisPay domain UUID via
 * {@link UserServiceClient#resolveUserId(Jwt)} rather than reading the JWT
 * {@code sub} claim directly.  This is required because:
 * <ul>
 *   <li>The {@code accounts} table uses AegisPay UUIDs (set at account creation from the
 *       {@code user.registered} Kafka event).</li>
 *   <li>The JWT {@code sub} claim is the Keycloak internal UUID — a completely different value.</li>
 *   <li>For first-session users (social login, or Keycloak-native users before async
 *       attribute write-back completes), the JWT carries no {@code aegispay_user_id} claim.
 *       Using {@code jwt.sub} as a fallback would always return an empty/404 account
 *       because no account is keyed by the Keycloak sub.</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService     ledgerService;
    private final TopUpService      topUpService;
    private final UserServiceClient userServiceClient;

    /**
     * Customer: get own account balances (one entry per currency).
     *
     * <p>UUID resolution: {@link UserServiceClient#resolveUserId(Jwt)} — fast path reads
     * {@code aegispay_user_id} claim; slow path calls {@code /api/v1/users/me} for
     * first-session users whose JWT doesn't carry the claim yet.
     */
    @GetMapping("/accounts/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = userServiceClient.resolveUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(ledgerService.getAccountsForUser(userId)));
    }

    /**
     * Back-office: get any user's accounts by their AegisPay domain UUID.
     * Only accessible to BACK_OFFICE, ADMIN, and MERCHANT_OPS roles.
     */
    @GetMapping("/accounts/{userId}")
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN', 'MERCHANT_OPS')")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAccountsForUser(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(ledgerService.getAccountsForUser(userId)));
    }

    /**
     * Get ledger entries for a specific transaction (double-entry audit trail).
     * Only accessible to BACK_OFFICE and ADMIN roles.
     */
    @GetMapping("/entries")
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<LedgerEntryResponse>>> getEntriesForTransaction(
            @RequestParam UUID transactionId) {
        return ResponseEntity.ok(ApiResponse.ok(ledgerService.getEntriesForTransaction(transactionId)));
    }

    // ── Wallet top-up ─────────────────────────────────────────────────────────

    /**
     * Step 1: Create a Stripe PaymentIntent.
     *
     * <p>Returns {@code clientSecret} that the Stripe SDK uses on the client to confirm payment.
     * The caller's AegisPay UUID is resolved via {@link UserServiceClient#resolveUserId(Jwt)}
     * so first-session social users can top up without needing {@code aegispay_user_id} in their JWT.
     */
    @PostMapping("/topup/intent")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<TopUpIntentResponse>> createTopUpIntent(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TopUpIntentRequest request) {
        UUID userId = userServiceClient.resolveUserId(jwt);
        TopUpIntentResponse response = topUpService.createIntent(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Step 2: Confirm top-up after the client has completed Stripe payment.
     *
     * <p>Verifies the Stripe PaymentIntent status server-side, then credits the caller's
     * account balance.  UUID resolution uses the same strategy as {@link #createTopUpIntent}.
     */
    @PostMapping("/topup/confirm")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<TopUpConfirmResponse>> confirmTopUp(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TopUpConfirmRequest request) {
        UUID userId = userServiceClient.resolveUserId(jwt);
        TopUpConfirmResponse response = topUpService.confirmTopUp(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
