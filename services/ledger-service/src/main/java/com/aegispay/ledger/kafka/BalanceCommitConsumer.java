package com.aegispay.ledger.kafka;

import com.aegispay.common.domain.event.BalanceCommitRequestedEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.ledger.service.LedgerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceCommitConsumer {

    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = KafkaTopics.BALANCE_COMMIT_REQUESTED,
        groupId = "ledger-service-commit"
    )
    public void handle(ConsumerRecord<String, String> record) {
        log.debug("Received balance.commit.requested: key={}", record.key());
        try {
            BalanceCommitRequestedEvent event =
                    objectMapper.readValue(record.value(), BalanceCommitRequestedEvent.class);
            ledgerService.commitBalance(event);
        } catch (Exception e) {
            log.error("Error processing balance.commit.requested: key={} error={}",
                    record.key(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
