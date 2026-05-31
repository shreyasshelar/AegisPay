package com.aegispay.ledger.domain.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TopUpConfirmResponse(
        String     status,
        UUID       referenceId,
        BigDecimal amount,
        String     currency
) {}
