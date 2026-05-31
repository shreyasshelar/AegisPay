package com.aegispay.risk.rules;

import com.aegispay.common.domain.event.RiskAssessmentRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RulesEngine {

    private final List<RuleEvaluator> evaluators;

    public RuleResult evaluate(RiskAssessmentRequestedEvent event) {
        int totalScore = 0;
        List<String> flagged = new ArrayList<>();

        for (RuleEvaluator evaluator : evaluators) {
            try {
                int score = evaluator.evaluate(event);
                if (score > 0) {
                    flagged.add(evaluator.ruleName());
                    totalScore += score;
                    log.debug("Rule '{}' fired: score contribution={}", evaluator.ruleName(), score);
                }
            } catch (Exception e) {
                log.error("Rule '{}' threw exception — skipping: {}", evaluator.ruleName(), e.getMessage(), e);
            }
        }

        int capped = Math.min(totalScore, 100);
        log.info("Risk rules result: txn={} totalScore={} flagged={}",
                event.getTransactionId(), capped, flagged);
        return new RuleResult(capped, flagged);
    }
}
