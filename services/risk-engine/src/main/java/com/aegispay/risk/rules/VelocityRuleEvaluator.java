package com.aegispay.risk.rules;

import com.aegispay.common.domain.event.RiskAssessmentRequestedEvent;
import com.aegispay.risk.config.RiskProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fires when a user exceeds N transactions within a sliding window.
 * Uses Redis INCR with TTL as a simple counter per (userId, window).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VelocityRuleEvaluator implements RuleEvaluator {

    private final StringRedisTemplate redisTemplate;
    private final RiskProperties props;

    @Override
    public String ruleName() {
        return "VELOCITY_EXCEEDED";
    }

    @Override
    public int evaluate(RiskAssessmentRequestedEvent event) {
        String key = "risk:velocity:" + event.getUserId();
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(props.getVelocity().getWindowMinutes()));
        }

        if (count != null && count > props.getVelocity().getMaxTransactions()) {
            log.debug("Velocity rule fired: userId={} count={} window={}min",
                    event.getUserId(), count, props.getVelocity().getWindowMinutes());
            return 40;
        }
        return 0;
    }
}
