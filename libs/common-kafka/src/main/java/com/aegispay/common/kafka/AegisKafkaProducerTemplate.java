package com.aegispay.common.kafka;

import com.aegispay.common.domain.event.BaseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Typed wrapper around KafkaTemplate that:
 * - Serialises the event payload to JSON
 * - Propagates W3C traceparent and X-Correlation-ID as Kafka record headers
 * - Uses transactionId (or eventId) as the message key for partition affinity
 * - Returns a CompletableFuture for async send confirmation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AegisKafkaProducerTemplate {

    private static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    private static final String HEADER_TRACE_PARENT   = "traceparent";
    private static final String HEADER_EVENT_TYPE     = "event-type";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public <E extends BaseEvent> CompletableFuture<SendResult<String, String>> send(
            String topic, String key, E event) {

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialise event: " + event.getClass().getSimpleName(), e);
        }

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        addTraceHeaders(record, event);

        log.debug("Publishing event={} to topic={} key={} correlationId={}",
                  event.getClass().getSimpleName(), topic, key, event.getCorrelationId());

        return kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event={} topic={} key={}: {}",
                                  event.getClass().getSimpleName(), topic, key, ex.getMessage());
                    } else {
                        log.debug("Published event={} partition={} offset={}",
                                  event.getClass().getSimpleName(),
                                  result.getRecordMetadata().partition(),
                                  result.getRecordMetadata().offset());
                    }
                });
    }

    private void addTraceHeaders(ProducerRecord<String, String> record, BaseEvent event) {
        String correlationId = event.getCorrelationId() != null
                ? event.getCorrelationId()
                : MDC.get("correlationId");
        String traceParent = event.getTraceParent() != null
                ? event.getTraceParent()
                : MDC.get("traceparent");

        if (correlationId != null) {
            record.headers().add(new RecordHeader(HEADER_CORRELATION_ID,
                    correlationId.getBytes(StandardCharsets.UTF_8)));
        }
        if (traceParent != null) {
            record.headers().add(new RecordHeader(HEADER_TRACE_PARENT,
                    traceParent.getBytes(StandardCharsets.UTF_8)));
        }
        record.headers().add(new RecordHeader(HEADER_EVENT_TYPE,
                event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8)));
    }
}
