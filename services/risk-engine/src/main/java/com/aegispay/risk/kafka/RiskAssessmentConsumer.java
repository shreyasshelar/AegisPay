package com.aegispay.risk.kafka;

import com.aegispay.common.domain.event.RiskAssessmentRequestedEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.risk.service.RiskScoringService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskAssessmentConsumer {

    private final RiskScoringService riskScoringService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.RISK_ASSESSMENT_REQUESTED, groupId = "risk-engine-assess")
    public void handle(ConsumerRecord<String, String> record) {
        log.debug("Received risk.assessment.requested: key={}", record.key());
        try {
            RiskAssessmentRequestedEvent event =
                    objectMapper.readValue(record.value(), RiskAssessmentRequestedEvent.class);
            riskScoringService.assess(event);
        } catch (Exception e) {
            log.error("Error processing risk assessment for key={}: {}", record.key(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
