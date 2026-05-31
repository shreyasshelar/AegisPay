package com.aegispay.risk.domain.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class RiskCaseResponse {
    UUID id;
    UUID transactionId;
    UUID userId;
    int riskScore;
    String decision;
    List<String> ruleFlags;
    String ragExplanation;
    Instant createdAt;
}
