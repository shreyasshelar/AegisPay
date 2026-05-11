package com.aegispay.gateway.filter;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.common.domain.dto.ErrorResponse;
import com.aegispay.gateway.config.GatewayProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Fixed-window rate limiter backed by Redis.
 *
 * Strategy (dual-key):
 *   Authenticated: key = "rl:user:{sub}"  — per-user quota
 *   Anonymous:     key = "rl:ip:{addr}"   — per-IP fallback quota
 *
 * Algorithm:
 *   1. INCR the current-window key (atomic)
 *   2. On first increment, EXPIRE the key at windowEnd
 *   3. If count > limit → 429 with Retry-After header
 *   4. Always add X-RateLimit-{Limit,Remaining,Reset} headers
 *
 * This GlobalFilter applies to EVERY route, so rate-limit enforcement is
 * never accidentally bypassed by a misconfigured route definition.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingGlobalFilter implements GlobalFilter, Ordered {

    private static final String ACTUATOR_PREFIX = "/actuator";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Health/metrics probes bypass rate limiting
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith(ACTUATOR_PREFIX)) {
            return chain.filter(exchange);
        }

        GatewayProperties.RateLimiter cfg = props.getRateLimiter();
        long windowStart = Instant.now().getEpochSecond() / cfg.getWindowSeconds();

        return resolveRateLimitKey(exchange, windowStart)
                .flatMap(key -> checkAndCount(key, cfg))
                .flatMap(result -> {
                    long count   = result.count();
                    long limit   = result.limit();
                    long remaining = Math.max(0, limit - count);
                    long resetAt = (windowStart + 1) * cfg.getWindowSeconds();

                    setRateLimitHeaders(exchange, limit, remaining, resetAt, cfg);

                    if (count > limit) {
                        log.warn("Rate limit exceeded: key={} count={} limit={}", result.key(), count, limit);
                        return rejectWithTooManyRequests(exchange, resetAt);
                    }
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        return -170;   // after JWT relay (-180), before routing
    }

    // ── Key resolution ─────────────────────────────────────────────────────────

    private Mono<String> resolveRateLimitKey(ServerWebExchange exchange, long window) {
        return exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwt -> "rl:user:" + jwt.getToken().getSubject() + ":" + window)
                .switchIfEmpty(Mono.fromSupplier(() ->
                        "rl:ip:" + resolveClientIp(exchange) + ":" + window));
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        // Respect X-Forwarded-For set by the ingress / load balancer
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    // ── Redis counter ──────────────────────────────────────────────────────────

    private Mono<CountResult> checkAndCount(String key, GatewayProperties.RateLimiter cfg) {
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    Mono<Boolean> expireMono = count == 1
                            ? redisTemplate.expire(key, Duration.ofSeconds(cfg.getKeyTtlSeconds()))
                            : Mono.just(Boolean.FALSE);
                    return expireMono.thenReturn(new CountResult(key, count, cfg.getMaxRequests()));
                });
    }

    // ── Response helpers ───────────────────────────────────────────────────────

    private void setRateLimitHeaders(ServerWebExchange exchange,
                                     long limit, long remaining, long resetAt,
                                     GatewayProperties.RateLimiter cfg) {
        exchange.getResponse().beforeCommit(() -> {
            try {
                var headers = exchange.getResponse().getHeaders();
                headers.set(cfg.getLimitHeader(),     String.valueOf(limit));
                headers.set(cfg.getRemainingHeader(), String.valueOf(remaining));
                headers.set(cfg.getResetHeader(),     String.valueOf(resetAt));
            } catch (UnsupportedOperationException ignored) {
                // Response already committed (e.g. circuit-breaker fallback wrote
                // the response before this beforeCommit hook ran).  Skip silently
                // so the exception does not propagate and trip the circuit breaker.
            }
            return Mono.empty();
        });
    }

    private Mono<Void> rejectWithTooManyRequests(ServerWebExchange exchange, long resetAt) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(resetAt - Instant.now().getEpochSecond()));

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("RATE_LIMIT_EXCEEDED")
                .message("Too many requests. Please slow down and retry after the reset window.")
                .httpStatus(HttpStatus.TOO_MANY_REQUESTS.value())
                .build();

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(ApiResponse.error(error));
        } catch (JsonProcessingException e) {
            body = "{\"success\":false,\"error\":{\"errorCode\":\"RATE_LIMIT_EXCEEDED\"}}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    // ── Value object ───────────────────────────────────────────────────────────

    private record CountResult(String key, long count, long limit) {}
}
