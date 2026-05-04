package com.aegispay.transaction.domain.dto;

import com.aegispay.common.domain.enums.TransactionStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record TransactionResponse(
    UUID id,
    UUID userId,
    UUID payerId,
    UUID payeeId,
    BigDecimal amount,
    String currency,
    TransactionStatus status,
    String idempotencyKey,
    UUID sagaId,
    Instant initiatedAt,
    Instant completedAt,
    String failureReason,
    String externalReference
) {}
