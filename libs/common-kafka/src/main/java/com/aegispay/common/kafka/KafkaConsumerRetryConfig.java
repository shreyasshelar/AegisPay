package com.aegispay.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Platform-wide Kafka consumer error handler.
 *
 * Retry policy: 3 attempts with exponential backoff (1s → 2s → 4s).
 * After exhaustion the record is forwarded to the DLQ via DlqPublisher.
 *
 * Non-retryable exceptions (e.g. deserialization failures) are sent directly
 * to the DLQ without retries.
 */
@Slf4j
@Configuration
public class KafkaConsumerRetryConfig {

    private static final long INITIAL_INTERVAL_MS = 1_000L;
    private static final double MULTIPLIER         = 2.0;
    private static final long MAX_INTERVAL_MS      = 10_000L;
    private static final long MAX_ELAPSED_MS       = 30_000L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DlqPublisher dlqPublisher) {
        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_INTERVAL_MS, MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MS);
        backOff.setMaxElapsedTime(MAX_ELAPSED_MS);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> {
                    log.error("Kafka consumer exhausted retries for topic={} partition={} offset={}: {}",
                              record.topic(), record.partition(), record.offset(), exception.getMessage());
                    dlqPublisher.publishToDlq(record, exception);
                },
                backOff
        );

        // These exceptions won't be retried — send straight to DLQ
        errorHandler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class,
                ListenerExecutionFailedException.class
        );

        return errorHandler;
    }
}
