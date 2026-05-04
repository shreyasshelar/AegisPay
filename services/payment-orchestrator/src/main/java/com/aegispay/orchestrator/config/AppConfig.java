package com.aegispay.orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableJpaAuditing
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public WebClient paymentGatewayWebClient(
            @Value("${aegispay.external-payment-gateway.base-url}") String baseUrl,
            @Value("${aegispay.external-payment-gateway.connect-timeout-ms:5000}") int connectMs,
            @Value("${aegispay.external-payment-gateway.read-timeout-ms:30000}") int readMs) {

        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
