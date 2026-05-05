package com.aegispay.orchestrator.domain.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class SagaStatusResponse {
    UUID sagaId;
    UUID transactionId;
    String currentStep;
    String status;
    BigDecimal amount;
    String currency;
    Instant startedAt;
    Instant updatedAt;
    Instant completedAt;
    String failureReason;
    String externalReference;
    List<SagaStepDto> steps;
}
