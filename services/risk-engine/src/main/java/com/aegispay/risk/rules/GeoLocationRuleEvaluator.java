package com.aegispay.risk.rules;

import com.aegispay.common.domain.event.RiskAssessmentRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fires when the transaction country differs from the account's home country.
 * Simplified: checks for a non-null payeeCountry that differs from a hard-coded
 * home-country placeholder. Full implementation would query user-service for the
 * account home country via a cached lookup.
 */
@Slf4j
@Component
public class GeoLocationRuleEvaluator implements RuleEvaluator {

    private static final String HOME_COUNTRY = "IN";

    @Override
    public String ruleName() {
        return "GEO_MISMATCH";
    }

    @Override
    public int evaluate(RiskAssessmentRequestedEvent event) {
        if (event.getPayeeCountry() != null && !HOME_COUNTRY.equalsIgnoreCase(event.getPayeeCountry())) {
            log.debug("Geo rule fired: txn={} payeeCountry={}", event.getTransactionId(), event.getPayeeCountry());
            return 20;
        }
        return 0;
    }
}
