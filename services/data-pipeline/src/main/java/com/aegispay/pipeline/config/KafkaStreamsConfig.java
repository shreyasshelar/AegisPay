package com.aegispay.pipeline.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;

import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.streams.StreamsConfig.*;

/**
 * Kafka Streams infrastructure configuration.
 *
 * <p>Wires:
 * <ul>
 *   <li>A {@link KafkaStreamsConfiguration} bean (picked up by Spring's
 *       {@link KafkaStreamsDefaultConfiguration})</li>
 *   <li>An uncaught-exception handler that logs the error and replaces the
 *       failed thread instead of shutting the entire application down</li>
 * </ul>
 */
@Slf4j
@Configuration
public class KafkaStreamsConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.streams.application-id}")
    private String applicationId;

    @Value("${spring.kafka.streams.default-key-serde}")
    private String defaultKeySerde;

    @Value("${spring.kafka.streams.default-value-serde}")
    private String defaultValueSerde;

    @Value("${spring.kafka.streams.properties.processing.guarantee:at_least_once}")
    private String processingGuarantee;

    @Value("${spring.kafka.streams.properties.commit.interval.ms:1000}")
    private String commitIntervalMs;

    /**
     * Primary {@link KafkaStreamsConfiguration} bean.
     *
     * <p>The bean name must match
     * {@link KafkaStreamsDefaultConfiguration#DEFAULT_STREAMS_CONFIG_BEAN_NAME}
     * so that Spring auto-creates the {@code StreamsBuilderFactoryBean}.
     */
    @Bean(KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kafkaStreamsConfiguration() {
        Map<String, Object> props = new HashMap<>();
        props.put(APPLICATION_ID_CONFIG, applicationId);
        props.put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(DEFAULT_KEY_SERDE_CLASS_CONFIG, defaultKeySerde);
        props.put(DEFAULT_VALUE_SERDE_CLASS_CONFIG, defaultValueSerde);
        props.put(PROCESSING_GUARANTEE_CONFIG, processingGuarantee);
        props.put(COMMIT_INTERVAL_MS_CONFIG, Long.parseLong(commitIntervalMs));

        // Tune for low-latency analytics
        props.put(CACHE_MAX_BYTES_BUFFERING_CONFIG, 10 * 1024 * 1024L); // 10 MB
        props.put(NUM_STREAM_THREADS_CONFIG, 2);

        log.info("Kafka Streams configured: applicationId={}, bootstrapServers={}", applicationId, bootstrapServers);
        return new KafkaStreamsConfiguration(props);
    }

    /**
     * Registers a global uncaught exception handler so that a transient error
     * in one stream thread replaces only that thread rather than killing the JVM.
     */
    @Bean
    public StreamsBuilderFactoryBeanConfigurer streamsBuilderFactoryBeanConfigurer() {
        return factoryBean -> factoryBean.setKafkaStreamsCustomizer(kafkaStreams ->
                kafkaStreams.setUncaughtExceptionHandler(exception -> {
                    log.error("Uncaught exception in Kafka Streams thread — replacing thread. Cause: {}",
                            exception.getMessage(), exception);
                    return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
                })
        );
    }
}
