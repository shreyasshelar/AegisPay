package com.aegispay.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Ensures every request through the gateway carries an X-Correlation-ID.
 * If the inbound request already has one (from a client or upstream proxy) it is
 * preserved; otherwise a new UUID is generated.
 *
 * The ID is echoed back in the response header so callers can correlate logs
 * across service boundaries without parsing the response body.
 *
 * Runs at ORDER = -200 (before JWT relay and tracing) so every subsequent filter
 * in the chain already sees the header in the MDC-equivalent exchange attributes.
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

        // Attach to exchange attributes so other filters can read it
        exchange.getAttributes().put(EXCHANGE_ATTR_KEY, finalCorrelationId);

        // Mutate the downstream request to carry the header
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        // Mutate the response to echo it back to the caller
        ServerHttpResponse mutatedResponse = exchange.getResponse();
        mutatedResponse.getHeaders().set(CORRELATION_ID_HEADER, finalCorrelationId);

        log.debug("correlationId={} path={}", finalCorrelationId,
                  exchange.getRequest().getPath().value());

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
