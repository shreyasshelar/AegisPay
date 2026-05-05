package com.aegispay.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Implements W3C Trace Context propagation (https://www.w3.org/TR/trace-context/).
 *
 * If the inbound request already contains a valid traceparent header, it is
 * forwarded unchanged — the gateway is a transparent hop in an existing trace.
 *
 * If no traceparent is present (e.g. a raw client call), a new one is generated:
 *   traceparent: 00-{32-hex traceId}-{16-hex spanId}-01
 *
 * The header is echoed back in the response so browser DevTools and API clients
 * can correlate the request without access to internal logs.
 *
 * Runs at ORDER = -190 (after CorrelationIdFilter, before JwtRelayFilter).
 */
@Slf4j
@Component
public class TraceParentGatewayFilter implements GatewayFilter, Ordered {

    private static final String HEADER_TRACE_PARENT = "traceparent";
    private static final String HEADER_TRACE_STATE  = "tracestate";
    private static final String TRACE_VERSION       = "00";
    private static final String TRACE_FLAGS         = "01";    // sampled

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

        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .header(HEADER_TRACE_PARENT, traceParent);

        // Preserve tracestate if present
        String traceState = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_STATE);
        if (traceState != null) {
            requestBuilder.header(HEADER_TRACE_STATE, traceState);
        }

        exchange.getResponse().getHeaders().set(HEADER_TRACE_PARENT, traceParent);

        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
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

    /**
     * A valid traceparent matches: version(2)-traceId(32)-spanId(16)-flags(2).
     * We only check structural validity, not semantic validity of the hex values.
     */
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
