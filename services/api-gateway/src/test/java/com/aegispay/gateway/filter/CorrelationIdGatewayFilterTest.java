package com.aegispay.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdGatewayFilterTest {

    private final CorrelationIdGatewayFilter filter = new CorrelationIdGatewayFilter();

    @Test
    void generatesCorrelationIdWhenAbsent() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());

        filter.filter(exchange, exch -> {
            String id = exch.getRequest().getHeaders().getFirst("X-Correlation-ID");
            assertThat(id).isNotNull().isNotBlank();
            assertThat((String) exch.getAttributes().get("correlationId")).isEqualTo(id);
            return Mono.empty();
        }).block();
    }

    @Test
    void preservesExistingCorrelationId() {
        String existingId = "test-correlation-id-123";
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header("X-Correlation-ID", existingId)
                        .build());

        filter.filter(exchange, exch -> {
            String id = exch.getRequest().getHeaders().getFirst("X-Correlation-ID");
            assertThat(id).isEqualTo(existingId);
            return Mono.empty();
        }).block();
    }

    @Test
    void echoesCorrelationIdInResponse() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());

        filter.filter(exchange, exch -> Mono.empty()).block();

        String responseId = exchange.getResponse().getHeaders().getFirst("X-Correlation-ID");
        assertThat(responseId).isNotNull().isNotBlank();
    }

    @Test
    void hasCorrectOrder() {
        assertThat(filter.getOrder()).isEqualTo(-200);
    }
}
