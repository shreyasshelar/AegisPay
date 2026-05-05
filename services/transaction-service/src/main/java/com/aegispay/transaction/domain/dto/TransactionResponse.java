package com.aegispay.transaction.domain.dto;

import com.aegispay.common.domain.enums.TransactionStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record TransactionResponse(
    /** Serialised as "transactionId" to match frontend contract. */
    @JsonProperty("transactionId") UUID id,
    UUID userId,
    UUID payerId,
    UUID payeeId,
    BigDecimal amount,
    String currency,
    TransactionStatus status,
    String idempotencyKey,
    UUID sagaId,
    /** Optional user-supplied memo; extracted from the metadata JSON blob. */
    String note,
    Instant initiatedAt,
    Instant completedAt,
    String failureReason,
    String externalReference
) {}
