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

    /**
     * UUID of the pre-created {@code KycDocument} row.
     *
     * <p>Nullable: in the async direct-upload flow (browser → AI Platform → callback)
     * no document row is pre-created by User Service. When {@code null}, User Service
     * creates a new {@code KycDocument} record inline inside {@code processAiCallback}.
     *
     * <p>Non-null: in the legacy user-service–initiated flow where User Service calls
     * AI Platform with a document reference and the document row was already persisted.
     */
    UUID documentId,

    @NotNull KycStatus newStatus,

    /**
     * Human-readable document category (NATIONAL_ID, PASSPORT, DRIVING_LICENSE, PAN_CARD).
     * Required when {@code documentId} is null so the inline-created document record
     * has a meaningful type.
     */
    String documentType,

    String rejectionReason,

    Map<String, Object> extractedData,

    Boolean tamperedFlag,

    BigDecimal qualityScore
) {}
