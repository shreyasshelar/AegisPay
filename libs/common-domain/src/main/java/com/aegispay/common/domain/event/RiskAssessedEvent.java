package com.aegispay.common.domain.event;

import com.aegispay.common.domain.enums.RiskDecision;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@SuperBuilder
public class RiskAssessedEvent extends BaseEvent {

    private UUID transactionId;
    private UUID sagaId;
    private UUID userId;
    private int riskScore;
    private RiskDecision decision;
    private List<String> flaggedRules;
    private String ragExplanation;
}
