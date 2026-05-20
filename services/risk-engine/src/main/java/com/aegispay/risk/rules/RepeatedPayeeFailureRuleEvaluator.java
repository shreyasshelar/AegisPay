package com.aegispay.risk.rules;

import com.aegispay.common.domain.event.RiskAssessmentRequestedEvent;
import com.aegispay.risk.repository.RiskCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fires when a user has previously sent money to the same payee and that transaction failed
 * (e.g. REJECTED by risk, payment gateway error, or ROLLED_BACK).
 *
 * <p>Pattern: retrying a failed payee can indicate:
 * <ul>
 *   <li>Social engineering — victim being coached to retry after a blocked transfer
 *   <li>Account testing — attacker probing payout paths with the same mule account
 * </ul>
 *
 * <p>Score 35: enough to tip a borderline case into REVIEW but not auto-reject on its own,
 * since legitimate retries (e.g. after network failure) are also common.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepeatedPayeeFailureRuleEvaluator implements RuleEvaluator {

    private final RiskCaseRepository riskCaseRepository;

    @Override
    public String ruleName() {
        return "REPEATED_PAYEE_FAILURE";
    }

    @Override
    public int evaluate(RiskAssessmentRequestedEvent event) {
        if (event.getPayeeId() == null) return 0;

        boolean hasPriorFailure = riskCaseRepository
                .existsByUserIdAndPayeeIdAndDecisionIn(
                        event.getUserId(),
                        event.getPayeeId(),
                        java.util.List.of("REJECTED"));

        if (hasPriorFailure) {
            log.warn("Repeated-payee-failure rule fired: userId={} payeeId={}",
                    event.getUserId(), event.getPayeeId());
            return 35;
        }
        return 0;
    }
}
