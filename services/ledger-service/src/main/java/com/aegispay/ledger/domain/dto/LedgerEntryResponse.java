package com.aegispay.ledger.domain.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class LedgerEntryResponse {
    UUID id;
    UUID accountId;
    UUID transactionId;
    String entryType;
    BigDecimal amount;
    BigDecimal balanceBefore;
    BigDecimal balanceAfter;
    String description;
    Instant createdAt;
}
