package com.aegispay.transaction.domain.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** Lightweight status-only response — sent over WebSocket and returned by the status endpoint. */
@Builder
public record TransactionStatusResponse(
    UUID transactionId,
    String status,
    String lastEvent,
    Instant updatedAt,
    String failureReason,  // non-null only on FAILED / ROLLED_BACK
    String failureCode,    // machine-readable code (e.g. "amount_too_small")
    String aiExplanation   // delay reason / ETA from AI platform (may be null)
) {}
