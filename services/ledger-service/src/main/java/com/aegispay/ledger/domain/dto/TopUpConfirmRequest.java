package com.aegispay.ledger.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record TopUpConfirmRequest(
        @NotBlank
        String paymentIntentId
) {}
