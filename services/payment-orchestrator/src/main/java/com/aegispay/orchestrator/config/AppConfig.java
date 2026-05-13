package com.aegispay.orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
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

    @Value("${aegispay.payment-gateway.base-url:https://api.stripe.com/v1}")
    private String paymentGatewayBaseUrl;

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * WebClient for the external payment gateway (Stripe).
     * Named to match the field in ExternalPaymentGatewayClient.
     */
    @Bean("paymentGatewayWebClient")
    public WebClient paymentGatewayWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(paymentGatewayBaseUrl)
                .build();
    }
}
