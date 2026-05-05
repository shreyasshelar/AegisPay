package com.aegispay.risk.rules;

import com.aegispay.common.domain.event.RiskAssessmentRequestedEvent;
import com.aegispay.risk.config.RiskProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RulesEngineTest {

    @Test
    void evaluate_aggregates_scores_and_caps_at_100() {
        RuleEvaluator r1 = mockRule("RULE_A", 60);
        RuleEvaluator r2 = mockRule("RULE_B", 70);
        RulesEngine engine = new RulesEngine(List.of(r1, r2));

        RuleResult result = engine.evaluate(event());

        assertThat(result.getTotalScore()).isEqualTo(100);
        assertThat(result.getFlaggedRules()).containsExactlyInAnyOrder("RULE_A", "RULE_B");
    }

    @Test
    void evaluate_zero_score_rule_not_flagged() {
        RuleEvaluator r1 = mockRule("FIRED", 25);
        RuleEvaluator r2 = mockRule("SILENT", 0);
        RulesEngine engine = new RulesEngine(List.of(r1, r2));

        RuleResult result = engine.evaluate(event());

        assertThat(result.getFlaggedRules()).containsExactly("FIRED");
        assertThat(result.getTotalScore()).isEqualTo(25);
    }

    @Test
    void evaluate_rule_exception_is_skipped() {
        RuleEvaluator bad = mock(RuleEvaluator.class);
        when(bad.ruleName()).thenReturn("THROWS");
        when(bad.evaluate(any())).thenThrow(new RuntimeException("oops"));
        RuleEvaluator good = mockRule("GOOD", 20);
        RulesEngine engine = new RulesEngine(List.of(bad, good));

        RuleResult result = engine.evaluate(event());

        assertThat(result.getTotalScore()).isEqualTo(20);
        assertThat(result.getFlaggedRules()).containsExactly("GOOD");
    }

    @Test
    void amountRule_fires_above_threshold() {
        RiskProperties props = new RiskProperties();
        props.getAmountThreshold().setUnverifiedKycLimit(new BigDecimal("10000"));
        AmountRuleEvaluator evaluator = new AmountRuleEvaluator(props);

        RiskAssessmentRequestedEvent highAmount = event(new BigDecimal("15000"));
        assertThat(evaluator.evaluate(highAmount)).isGreaterThan(0);

        RiskAssessmentRequestedEvent lowAmount = event(new BigDecimal("500"));
        assertThat(evaluator.evaluate(lowAmount)).isEqualTo(0);
    }

    @Test
    void geoRule_fires_on_foreign_country() {
        GeoLocationRuleEvaluator evaluator = new GeoLocationRuleEvaluator();

        RiskAssessmentRequestedEvent foreign = eventWithCountry("US");
        assertThat(evaluator.evaluate(foreign)).isGreaterThan(0);

        RiskAssessmentRequestedEvent home = eventWithCountry("IN");
        assertThat(evaluator.evaluate(home)).isEqualTo(0);
    }

    private RuleEvaluator mockRule(String name, int score) {
        RuleEvaluator r = mock(RuleEvaluator.class);
        when(r.ruleName()).thenReturn(name);
        when(r.evaluate(any())).thenReturn(score);
        return r;
    }

    private RiskAssessmentRequestedEvent event() {
        return event(new BigDecimal("500"));
    }

    private RiskAssessmentRequestedEvent event(BigDecimal amount) {
        return RiskAssessmentRequestedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(UUID.randomUUID()).sagaId(UUID.randomUUID())
                .userId(UUID.randomUUID()).amount(amount).currency("USD")
                .build();
    }

    private RiskAssessmentRequestedEvent eventWithCountry(String country) {
        return RiskAssessmentRequestedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(UUID.randomUUID()).sagaId(UUID.randomUUID())
                .userId(UUID.randomUUID()).amount(new BigDecimal("100")).currency("USD")
                .payeeCountry(country)
                .build();
    }
}
