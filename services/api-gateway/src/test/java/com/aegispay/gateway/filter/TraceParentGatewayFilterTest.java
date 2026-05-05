package com.aegispay.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class TraceParentGatewayFilterTest {

    private final TraceParentGatewayFilter filter = new TraceParentGatewayFilter();

    @Test
    void generatesValidTraceParentWhenAbsent() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());

        filter.filter(exchange, exch -> {
            String tp = exch.getRequest().getHeaders().getFirst("traceparent");
            assertThat(tp).isNotNull();
            String[] parts = tp.split("-");
            assertThat(parts).hasSize(4);
            assertThat(parts[0]).isEqualTo("00");
            assertThat(parts[1]).hasSize(32);
            assertThat(parts[2]).hasSize(16);
            assertThat(parts[3]).isEqualTo("01");
            return Mono.empty();
        }).block();
    }

    @Test
    void preservesValidInboundTraceParent() {
        String inbound = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header("traceparent", inbound)
                        .build());

        filter.filter(exchange, exch -> {
            String tp = exch.getRequest().getHeaders().getFirst("traceparent");
            assertThat(tp).isEqualTo(inbound);
            return Mono.empty();
        }).block();
    }

    @Test
    void rejectsAndRegeneratesMalformedTraceParent() {
        String malformed = "bad-traceparent";
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header("traceparent", malformed)
                        .build());

        filter.filter(exchange, exch -> {
            String tp = exch.getRequest().getHeaders().getFirst("traceparent");
            assertThat(tp).isNotEqualTo(malformed);
            assertThat(tp.split("-")).hasSize(4);
            return Mono.empty();
        }).block();
    }

    @Test
    void echoesTraceParentInResponse() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());

        filter.filter(exchange, exch -> Mono.empty()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("traceparent")).isNotNull();
    }

    @Test
    void hasCorrectOrder() {
        assertThat(filter.getOrder()).isEqualTo(-190);
    }
}
