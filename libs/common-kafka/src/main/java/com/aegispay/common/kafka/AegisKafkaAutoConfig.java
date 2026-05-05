package com.aegispay.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;

@AutoConfiguration
@EnableKafka
@Import({KafkaConsumerRetryConfig.class})
public class AegisKafkaAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public DlqPublisher dlqPublisher(
            org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate) {
        return new DlqPublisher(kafkaTemplate);
    }

    @Bean
    public AegisKafkaProducerTemplate aegisKafkaProducerTemplate(
            org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        return new AegisKafkaProducerTemplate(kafkaTemplate, objectMapper);
    }
}
