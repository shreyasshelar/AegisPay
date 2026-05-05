package com.aegispay.user.domain.dto;

import com.aegispay.common.domain.enums.KycStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Callback from the AI platform after OCR + document analysis completes. */
@Builder
public record KycStatusUpdateRequest(

    @NotNull UUID documentId,

    @NotNull KycStatus newStatus,

    String rejectionReason,

    Map<String, Object> extractedData,

    Boolean tamperedFlag,

    BigDecimal qualityScore
) {}
