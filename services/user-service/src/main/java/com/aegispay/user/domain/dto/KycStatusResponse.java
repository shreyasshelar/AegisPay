package com.aegispay.user.domain.dto;

import com.aegispay.common.domain.enums.KycStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record KycStatusResponse(
    UUID userId,
    KycStatus kycStatus,
    String documentType,
    String ocrStatus,
    Boolean tamperedFlag,
    String rejectionReason
) {}
