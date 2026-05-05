package com.aegispay.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record KycDocumentUploadRequest(

    /** AADHAAR | PAN | PASSPORT | DRIVING_LICENSE */
    @NotBlank String documentType,

    /** Pre-signed S3/GCS object key for the uploaded document image. */
    @NotBlank String documentRef
) {}
