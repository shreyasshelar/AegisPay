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

import java.util.UUID;

/**
 * Ensures every request through the gateway carries an X-Correlation-ID.
 * If the inbound request already has one it is preserved; otherwise a new UUID is generated.
 * The ID is echoed back in the response header.
 *
 * Uses ServerHttpRequestDecorator to add headers because Spring WebFlux's
 * DefaultServerHttpRequestBuilder.header() retains a reference to the original
 * ReadOnlyHttpHeaders rather than making a mutable copy, causing UnsupportedOperationException.
 */
@Slf4j
@Component
public class CorrelationIdGatewayFilter implements GatewayFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String EXCHANGE_ATTR_KEY     = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        final String finalCorrelationId = correlationId;
        exchange.getAttributes().put(EXCHANGE_ATTR_KEY, finalCorrelationId);

        // Build a mutable copy of the headers and inject the correlation ID
        LinkedMultiValueMap<String, String> mutableHeaders =
                new LinkedMultiValueMap<>(exchange.getRequest().getHeaders());
        mutableHeaders.set(CORRELATION_ID_HEADER, finalCorrelationId);
        HttpHeaders newHeaders = new HttpHeaders(mutableHeaders);

        ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            @NonNull
            public HttpHeaders getHeaders() {
                return newHeaders;
            }
        };

        // Echo the ID back to the caller via the response header.
        // Set directly (not via beforeCommit) so it is visible in tests and
        // in any downstream filter that reads the response headers before body write.
        try {
            exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, finalCorrelationId);
        } catch (UnsupportedOperationException ignored) {
            // Headers already committed (e.g. circuit-breaker fallback) — skip
        }

        log.debug("correlationId={} path={}", finalCorrelationId,
                  exchange.getRequest().getPath().value());

        return chain.filter(exchange.mutate().request(decoratedRequest).build());
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
