package com.aegispay.common.domain.event;

import com.aegispay.common.domain.enums.RiskDecision;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

@Getter
@SuperBuilder
public class RiskAssessedEvent extends BaseEvent {

    private final UUID transactionId;
    private final UUID sagaId;
    private final UUID userId;
    private final int riskScore;
    private final RiskDecision decision;
    private final List<String> flaggedRules;
    private final String ragExplanation;
}
