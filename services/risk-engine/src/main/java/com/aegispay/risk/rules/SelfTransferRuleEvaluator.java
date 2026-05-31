package com.aegispay.risk.rules;

import com.aegispay.common.domain.event.RiskAssessmentRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Hard-blocks self-transfers (payer == payee).
 *
 * <p>A score of 100 guarantees a REJECTED decision regardless of other rules.
 * This catches both accidental same-account sends and attempts to test the
 * payment pipeline without a real counterparty.
 */
@Slf4j
@Component
public class SelfTransferRuleEvaluator implements RuleEvaluator {

    @Override
    public String ruleName() {
        return "SELF_TRANSFER";
    }

    @Override
    public int evaluate(RiskAssessmentRequestedEvent event) {
        if (event.getPayeeId() != null && event.getPayeeId().equals(event.getUserId())) {
            log.warn("Self-transfer detected: userId={}", event.getUserId());
            return 100;
        }
        return 0;
    }
}
