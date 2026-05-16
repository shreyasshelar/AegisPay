package com.aegispay.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Implements W3C Trace Context propagation (https://www.w3.org/TR/trace-context/).
 *
 * If the inbound request already contains a valid traceparent it is forwarded unchanged.
 * Otherwise a new traceparent is generated: 00-{32-hex traceId}-{16-hex spanId}-01
 *
 * Uses ServerHttpRequestDecorator to avoid UnsupportedOperationException on ReadOnlyHttpHeaders
 * that occurs when using ServerHttpRequest.mutate().header() in Spring WebFlux.
 */
@Slf4j
@Component
public class TraceParentGatewayFilter implements GatewayFilter, Ordered {

    private static final String HEADER_TRACE_PARENT = "traceparent";
    private static final String HEADER_TRACE_STATE  = "tracestate";
    private static final String TRACE_VERSION       = "00";
    private static final String TRACE_FLAGS         = "01";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String existingTraceParent = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_PARENT);

        String traceParent;
        if (isValidTraceParent(existingTraceParent)) {
            traceParent = existingTraceParent;
        } else {
            traceParent = generateTraceParent();
            log.debug("Generated new traceparent={}", traceParent);
        }

        exchange.getAttributes().put(HEADER_TRACE_PARENT, traceParent);

        LinkedMultiValueMap<String, String> mutableHeaders =
                new LinkedMultiValueMap<>(exchange.getRequest().getHeaders());
        mutableHeaders.set(HEADER_TRACE_PARENT, traceParent);

        String traceState = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_STATE);
        if (traceState != null) {
            mutableHeaders.set(HEADER_TRACE_STATE, traceState);
        }

        HttpHeaders newHeaders = new HttpHeaders(mutableHeaders);

        ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            @NonNull
            public HttpHeaders getHeaders() {
                return newHeaders;
            }
        };

        // Echo traceparent in the response header directly (not via beforeCommit) so it
        // is visible to downstream filters and test assertions before the body is written.
        final String finalTraceParent = traceParent;
        try {
            exchange.getResponse().getHeaders().set(HEADER_TRACE_PARENT, finalTraceParent);
        } catch (UnsupportedOperationException ignored) {
            // Headers already committed (e.g. circuit-breaker fallback) — skip
        }

        return chain.filter(exchange.mutate().request(decoratedRequest).build());
    }

    @Override
    public int getOrder() {
        return -190;
    }

    private String generateTraceParent() {
        byte[] traceIdBytes = new byte[16];
        byte[] spanIdBytes  = new byte[8];
        RANDOM.nextBytes(traceIdBytes);
        RANDOM.nextBytes(spanIdBytes);

        String traceId = HexFormat.of().formatHex(traceIdBytes);
        String spanId  = HexFormat.of().formatHex(spanIdBytes);

        return String.join("-", TRACE_VERSION, traceId, spanId, TRACE_FLAGS);
    }

    private boolean isValidTraceParent(String value) {
        if (value == null || value.isBlank()) return false;
        String[] parts = value.split("-");
        return parts.length == 4
               && parts[0].length() == 2
               && parts[1].length() == 32
               && parts[2].length() == 16
               && parts[3].length() == 2;
    }
}
