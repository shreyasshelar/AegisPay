package com.aegispay.risk.kafka;

import com.aegispay.common.domain.enums.KycStatus;
import com.aegispay.common.domain.event.KycStatusChangedEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.risk.config.RiskProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Tracks KYC approval status in Redis so AmountRuleEvaluator can check it without a DB call.
 * Key: "risk:kyc:{userId}" → "APPROVED" | "REJECTED" | ...
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KycStatusConsumer {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.KYC_STATUS_CHANGED, groupId = "risk-engine-kyc")
    public void handle(ConsumerRecord<String, String> record) {
        try {
            KycStatusChangedEvent event = objectMapper.readValue(record.value(), KycStatusChangedEvent.class);
            String key = "risk:kyc:" + event.getUserId();
            redisTemplate.opsForValue().set(key, event.getNewStatus().name(), Duration.ofDays(30));
            log.debug("Updated KYC status in Redis: userId={} status={}", event.getUserId(), event.getNewStatus());
        } catch (Exception e) {
            log.error("Failed to process KycStatusChangedEvent: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
