package com.aegispay.user.domain.dto;

import com.aegispay.common.domain.enums.KycStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Callback from the AI platform after OCR + document analysis completes.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} — tolerates extra fields
 * sent by future AI platform versions without a 500.
 *
 * <p>{@code newStatus} carries {@code @JsonAlias("status")} so manual API calls
 * and older AI platform versions that send {@code "status"} instead of
 * {@code "newStatus"} deserialize correctly.
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record KycStatusUpdateRequest(

    /**
     * UUID of the pre-created {@code KycDocument} row.
     *
     * <p>Nullable: in the async direct-upload flow (browser → AI Platform → callback)
     * no document row is pre-created by User Service. When {@code null}, User Service
     * creates a new {@code KycDocument} record inline inside {@code processAiCallback}.
     */
    UUID documentId,

    /**
     * New KYC status to transition to (e.g. APPROVED, REJECTED, AI_PROCESSING).
     * Accepts {@code "newStatus"} (canonical) or {@code "status"} (alias for
     * backward compatibility with manual API calls and older clients).
     */
    @JsonAlias("status")
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