package com.aegispay.ledger.kafka;

import com.aegispay.common.domain.event.BalanceRollbackRequestedEvent;
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
public class BalanceRollbackConsumer {

    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = KafkaTopics.BALANCE_ROLLBACK_REQUESTED,
        groupId = "ledger-service-rollback"
    )
    public void handle(ConsumerRecord<String, String> record) {
        log.debug("Received balance.rollback.requested: key={}", record.key());
        try {
            BalanceRollbackRequestedEvent event =
                    objectMapper.readValue(record.value(), BalanceRollbackRequestedEvent.class);
            ledgerService.rollbackBalance(event);
        } catch (Exception e) {
            log.error("Error processing balance.rollback.requested: key={} error={}",
                    record.key(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
