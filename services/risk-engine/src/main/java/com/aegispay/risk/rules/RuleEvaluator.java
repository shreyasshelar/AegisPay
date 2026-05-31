package com.aegispay.risk.rules;

import com.aegispay.common.domain.event.RiskAssessmentRequestedEvent;

public interface RuleEvaluator {

    /** Rule name, used in flaggedRules list. */
    String ruleName();

    /**
     * Evaluate this rule.
     * @return score contribution (0-100). 0 means rule did not fire.
     */
    int evaluate(RiskAssessmentRequestedEvent event);
}
