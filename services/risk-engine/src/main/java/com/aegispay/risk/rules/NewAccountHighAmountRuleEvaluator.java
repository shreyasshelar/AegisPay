package com.aegispay.risk.rules;

import com.aegispay.common.domain.event.RiskAssessmentRequestedEvent;
import com.aegispay.risk.config.RiskProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Flags transactions from newly-created accounts (< 24 hours old) that exceed a moderate amount.
 *
 * <p>New accounts are a common fraud vector — attackers create accounts, load funds,
 * and immediately try to withdraw or transfer out. A score of 50 puts the transaction
 * into REVIEW for amounts ≥ ₹5,000, which combined with other rules can push it to REJECTED.
 *
 * <p>{@code accountCreatedAt} is populated by the Payment Orchestrator from the saga's
 * start timestamp (proxy for account age). When absent, the rule does not fire to
 * avoid false positives on legacy events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewAccountHighAmountRuleEvaluator implements RuleEvaluator {

    private static final Duration NEW_ACCOUNT_THRESHOLD   = Duration.ofHours(24);
    private static final BigDecimal AMOUNT_THRESHOLD      = new BigDecimal("5000");

    private final RiskProperties props;

    @Override
    public String ruleName() {
        return "NEW_ACCOUNT_HIGH_AMOUNT";
    }

    @Override
    public int evaluate(RiskAssessmentRequestedEvent event) {
        if (event.getAccountCreatedAt() == null) return 0;
        if (event.getAmount() == null) return 0;

        boolean isNewAccount = Duration.between(event.getAccountCreatedAt(), Instant.now())
                .compareTo(NEW_ACCOUNT_THRESHOLD) < 0;

        boolean isHighAmount = event.getAmount()
                .compareTo(AMOUNT_THRESHOLD) >= 0;

        if (isNewAccount && isHighAmount) {
            log.warn("New-account high-amount rule fired: userId={} amount={} accountAge={}",
                    event.getUserId(), event.getAmount(),
                    Duration.between(event.getAccountCreatedAt(), Instant.now()).toMinutes() + " min");
            return 50;
        }
        return 0;
    }
}
