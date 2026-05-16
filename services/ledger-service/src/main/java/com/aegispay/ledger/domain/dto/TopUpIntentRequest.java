package com.aegispay.ledger.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TopUpIntentRequest(
        @NotNull
        @DecimalMin(value = "1.00", message = "Minimum top-up is 1.00")
        BigDecimal amount,

        @NotBlank
        @Size(min = 3, max = 3)
        String currency
) {}
