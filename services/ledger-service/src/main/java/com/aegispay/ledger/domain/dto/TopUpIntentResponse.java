package com.aegispay.ledger.domain.dto;

import java.math.BigDecimal;

public record TopUpIntentResponse(
        String paymentIntentId,
        String clientSecret,     // passed to Stripe SDK on the client to confirm payment
        BigDecimal amount,
        String currency
) {}
