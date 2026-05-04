package com.aegispay.ledger.domain.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class AccountResponse {
    UUID id;
    UUID userId;
    String currency;
    BigDecimal availableBalance;
    BigDecimal reservedBalance;
    BigDecimal totalBalance;
    Instant updatedAt;
}
