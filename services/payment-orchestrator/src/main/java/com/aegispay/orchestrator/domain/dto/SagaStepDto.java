package com.aegispay.orchestrator.domain.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class SagaStepDto {
    UUID id;
    String stepName;
    String stepStatus;
    int attemptCount;
    Instant lastAttemptAt;
    String errorMessage;
}
