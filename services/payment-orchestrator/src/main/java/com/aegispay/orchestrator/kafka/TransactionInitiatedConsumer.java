package com.aegispay.orchestrator.kafka;

import com.aegispay.common.domain.event.TransactionInitiatedEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.orchestrator.saga.PaymentSagaOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionInitiatedConsumer {

    private final PaymentSagaOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.TRANSACTION_INITIATED, groupId = "payment-orchestrator-initiated")
    public void handle(ConsumerRecord<String, String> record) {
        log.debug("Received transaction.initiated: key={}", record.key());
        try {
            TransactionInitiatedEvent event = objectMapper.readValue(record.value(), TransactionInitiatedEvent.class);
            orchestrator.startSaga(event);
        } catch (Exception e) {
            log.error("Error starting saga for txn={}: {}", record.key(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
