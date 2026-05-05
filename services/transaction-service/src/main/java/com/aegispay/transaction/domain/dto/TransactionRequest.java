package com.aegispay.transaction.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Builder
public record TransactionRequest(

    @NotNull(message = "payerId is required")
    UUID payerId,

    @NotNull(message = "payeeId is required")
    UUID payeeId,

    @NotNull @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 4)
    BigDecimal amount,

    @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter ISO 4217 code")
    String currency,

    /** Arbitrary client metadata (e.g. order ID, description). Stored as JSONB. */
    Map<String, Object> metadata
) {}
