package com.aegispay.transaction.controller;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.common.domain.dto.PagedResponse;
import com.aegispay.transaction.domain.dto.TransactionRequest;
import com.aegispay.transaction.domain.dto.TransactionResponse;
import com.aegispay.transaction.domain.dto.TransactionStatusResponse;
import com.aegispay.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /** Initiate a new payment transaction. */
    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> create(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = UUID.fromString(jwt.getClaimAsString("aegispay_user_id") != null
                ? jwt.getClaimAsString("aegispay_user_id")
                : jwt.getSubject());

        TransactionResponse response = transactionService.create(request, idempotencyKey, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    /** Get full transaction details (write-side). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(transactionService.getById(id)));
    }

    /** Lightweight status endpoint — served from MongoDB read model. */
    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TransactionStatusResponse>> getStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(transactionService.getStatus(id)));
    }

    /** Paginated transaction history for the authenticated user (from MongoDB). */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TransactionStatusResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = UUID.fromString(jwt.getClaimAsString("aegispay_user_id") != null
                ? jwt.getClaimAsString("aegispay_user_id")
                : jwt.getSubject());

        PagedResponse<TransactionStatusResponse> response =
                transactionService.listForUser(userId, page, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
