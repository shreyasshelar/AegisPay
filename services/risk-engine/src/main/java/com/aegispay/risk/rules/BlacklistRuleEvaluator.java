package com.aegispay.risk.rules;

import com.aegispay.common.domain.event.RiskAssessmentRequestedEvent;
import com.aegispay.risk.config.RiskProperties;
import com.aegispay.risk.repository.FraudBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Fires when the userId or IP appears on the fraud blacklist.
 * Checks Redis cache first (populated by BlacklistSyncService on add), then DB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlacklistRuleEvaluator implements RuleEvaluator {

    private final FraudBlacklistRepository blacklistRepository;
    private final StringRedisTemplate redisTemplate;
    private final RiskProperties props;

    @Override
    public String ruleName() {
        return "BLACKLISTED_ENTITY";
    }

    @Override
    public int evaluate(RiskAssessmentRequestedEvent event) {
        String prefix = props.getBlacklist().getRedisKeyPrefix();

        if (isBlacklisted(prefix, "USER", event.getUserId().toString())) {
            log.warn("Blacklist rule fired: userId={}", event.getUserId());
            return 100;
        }

        if (event.getIpAddress() != null && isBlacklisted(prefix, "IP", event.getIpAddress())) {
            log.warn("Blacklist rule fired: ip={}", event.getIpAddress());
            return 80;
        }

        return 0;
    }

    private boolean isBlacklisted(String prefix, String type, String value) {
        String redisKey = prefix + type + ":" + value;
        Boolean cached = redisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(cached)) return true;
        return blacklistRepository.existsByEntityTypeAndEntityValue(type, value);
    }
}
