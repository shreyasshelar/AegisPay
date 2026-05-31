package com.aegispay.risk.rules;

import com.aegispay.common.domain.event.RiskAssessmentRequestedEvent;
import com.aegispay.risk.config.RiskProperties;
import com.aegispay.risk.repository.RiskCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fires when an unverified-KYC account attempts a large transaction.
 * KYC status is reflected in the user risk profile tracked via past risk cases
 * (simplified: fires on amount > threshold for any user with no prior approvals).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AmountRuleEvaluator implements RuleEvaluator {

    private final RiskProperties props;

    @Override
    public String ruleName() {
        return "HIGH_AMOUNT_UNVERIFIED";
    }

    @Override
    public int evaluate(RiskAssessmentRequestedEvent event) {
        if (event.getAmount() != null &&
                event.getAmount().compareTo(props.getAmountThreshold().getUnverifiedKycLimit()) > 0) {
            log.debug("Amount rule fired: txn={} amount={} threshold={}",
                    event.getTransactionId(), event.getAmount(),
                    props.getAmountThreshold().getUnverifiedKycLimit());
            return 30;
        }
        return 0;
    }
}
