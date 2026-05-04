package com.aegispay.common.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Publishes poison-pill messages to the DLQ topic (<original-topic>.DLQ)
 * after the consumer has exhausted its retry attempts.
 *
 * DLQ records carry extra headers: original-topic, failure-reason, failure-timestamp
 * so ops can replay or triage them without parsing the payload.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqPublisher {

    private static final String HEADER_ORIGINAL_TOPIC    = "dlq-original-topic";
    private static final String HEADER_FAILURE_REASON    = "dlq-failure-reason";
    private static final String HEADER_FAILURE_TIMESTAMP = "dlq-failure-timestamp";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void publishToDlq(ConsumerRecord<String, String> failedRecord, Exception cause) {
        String dlqTopic = KafkaTopics.dlq(failedRecord.topic());

        ProducerRecord<String, String> dlqRecord =
                new ProducerRecord<>(dlqTopic, failedRecord.key(), failedRecord.value());

        for (Header h : failedRecord.headers()) {
            dlqRecord.headers().add(h);
        }
        dlqRecord.headers().add(header(HEADER_ORIGINAL_TOPIC, failedRecord.topic()));
        dlqRecord.headers().add(header(HEADER_FAILURE_REASON,
                cause != null ? cause.getMessage() : "unknown"));
        dlqRecord.headers().add(header(HEADER_FAILURE_TIMESTAMP, Instant.now().toString()));

        kafkaTemplate.send(dlqRecord);

        log.warn("Message published to DLQ: originalTopic={} dlqTopic={} key={} reason={}",
                 failedRecord.topic(), dlqTopic, failedRecord.key(),
                 cause != null ? cause.getMessage() : "unknown");
    }

    private RecordHeader header(String key, String value) {
        return new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8));
    }
}
