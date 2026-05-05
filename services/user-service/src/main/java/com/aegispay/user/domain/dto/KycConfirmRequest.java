package com.aegispay.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record KycConfirmRequest(
    /** AADHAAR | PAN | PASSPORT | DRIVING_LICENSE */
    @NotBlank String documentType
) {}
