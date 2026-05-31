package com.aegispay.transaction.controller;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.common.domain.dto.PagedResponse;
import com.aegispay.transaction.client.UserServiceClient;
import com.aegispay.transaction.domain.dto.TransactionRequest;
import com.aegispay.transaction.domain.dto.TransactionResponse;
import com.aegispay.transaction.domain.dto.TransactionStatusResponse;
import com.aegispay.transaction.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for the transaction write-path and read-model query.
 *
 * <h3>User identity resolution</h3>
 * Every endpoint that operates on behalf of the authenticated user calls
 * {@link UserServiceClient#resolveUserId} or {@link UserServiceClient#resolveCurrentUser}
 * to obtain the stable AegisPay domain UUID rather than reading directly from the JWT.
 * This prevents the <em>split-identity</em> problem that would occur if some transactions
 * were stored with the Keycloak subject (first session, no write-back yet) and others with
 * the AegisPay UUID (after write-back) — the transaction history would appear fragmented.
 *
 * <h3>KYC gate</h3>
 * {@code POST /api/v1/transactions} performs a KYC check in the controller layer, before
 * the service layer, so the check runs for every incoming creation request (including
 * idempotency retries).  This intentionally blocks retries if the user's KYC status was
 * revoked between attempts.
 */
@Validated
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final UserServiceClient  userServiceClient;

    /**
     * Initiate a new payment transaction.
     *
     * <p>Resolution order for the payer's UUID:
     * <ol>
     *   <li>Call {@link UserServiceClient#resolveCurrentUser()} — a single {@code /me} call
     *       that returns both the AegisPay UUID and the KYC status.</li>
     *   <li>If user-service is unavailable (circuit open), fall back to the
     *       {@code aegispay_user_id} JWT claim, then to the JWT subject as last resort.</li>
     * </ol>
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> create(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {

        // Single /me call: resolves the payer's stable AegisPay UUID + KYC status.
        // Using /me instead of /{userId}/kyc-status avoids:
        //   (a) The getByExternalId vs getById confusion in UserController
        //   (b) The PreAuthorize race for social users whose JWT has no aegispay_user_id yet
        //   (c) A second network call to resolve the UUID separately
        UserServiceClient.UserMeInfo me = userServiceClient.resolveCurrentUser();

        // Derive the stable payer UUID from /me result, falling back to JWT claims.
        UUID userId = me.id() != null
                ? me.id()
                : userServiceClient.resolveUserId(jwt);  // fast JWT path or last-resort sub

        // KYC gate: throws AegisPayException(FORBIDDEN) for REJECTED / INCOMPLETE status.
        // UNKNOWN (user-service unavailable) is allowed through; risk engine applies secondary check.
        userServiceClient.assertKycStatus(me.kycStatus());

        TransactionResponse response = transactionService.create(request, idempotencyKey, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    /** Get full transaction details (write-side Postgres read). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(transactionService.getById(id)));
    }

    /** Lightweight status endpoint — served from MongoDB read model. */
    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TransactionStatusResponse>> getStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(transactionService.getStatus(id)));
    }

    /**
     * Paginated transaction history for the authenticated user (from MongoDB read model).
     *
     * <p>Uses the same {@link UserServiceClient#resolveUserId} strategy as {@code create}
     * so list results are always consistent with the UUID stored in transactions — even for
     * social users on their first session.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TransactionResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String status,
            @RequestParam(required = false)    String fromDate,
            @RequestParam(required = false)    String toDate) {

        // resolveUserId is fast for established users (reads JWT claim, no network call).
        // For social first-session users it calls /me once to get the AegisPay UUID.
        UUID userId = userServiceClient.resolveUserId(jwt);

        PagedResponse<TransactionResponse> response =
                transactionService.listForUser(userId, page, size, status, fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
