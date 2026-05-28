package com.aegispay.gateway.filter;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.common.domain.dto.ErrorResponse;
import com.aegispay.gateway.config.GatewayProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Per-user KYC attempt rate limiter applied only to {@code POST /api/v1/ai/kyc/process}.
 *
 * <p>Prevents brute-forcing fake ID cards: 5 attempts per 24 hours per authenticated user.
 * Unauthenticated requests are rejected immediately (the route also requires auth, so this
 * is a defence-in-depth measure).
 *
 * <p>Redis key format: {@code kyc:rl:{userId}:{dayBucket}}
 * where dayBucket = epoch_seconds / 86400 (resets at midnight UTC).
 *
 * <p>The filter is applied per-route (not globally) — wire it only to the KYC route in
 * {@link com.aegispay.gateway.config.GatewayRoutingConfig}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KycRateLimitGatewayFilter implements GatewayFilter {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayProperties.KycRateLimiter cfg = props.getKycRateLimiter();
        long dayBucket = System.currentTimeMillis() / 1000 / (cfg.getWindowHours() * 3600L);

        return exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .switchIfEmpty(Mono.error(new IllegalStateException("Unauthenticated KYC request")))
                .flatMap(jwt -> {
                    String userId = jwt.getToken().getSubject();
                    String key    = "kyc:rl:" + userId + ":" + dayBucket;

                    return redisTemplate.opsForValue().increment(key)
                            .flatMap(count -> {
                                if (count == 1) {
                                    redisTemplate.expire(key, Duration.ofSeconds(cfg.getKeyTtlSeconds()))
                                            .subscribe();
                                }

                                long remaining = Math.max(0, cfg.getMaxAttempts() - count);
                                // Best-effort informational headers.  Spring Security's filter-chain
                                // wraps the exchange and can seal response headers as ReadOnlyHttpHeaders
                                // by the time this Lettuce IO-thread callback executes.  Swallow rather
                                // than crashing the entire request with a 500 INTERNAL_SERVER_ERROR.
                                try {
                                    exchange.getResponse().getHeaders()
                                            .set("X-KYC-RateLimit-Remaining", String.valueOf(remaining));
                                    exchange.getResponse().getHeaders()
                                            .set("X-KYC-RateLimit-Limit",
                                                    String.valueOf(cfg.getMaxAttempts()));
                                } catch (UnsupportedOperationException ignored) {
                                    // Rate-limit info headers are informational only; skip if read-only
                                }

                                if (count > cfg.getMaxAttempts()) {
                                    log.warn("KYC rate limit exceeded: userId={} count={}", userId, count);
                                    return rejectKycExceeded(exchange, cfg.getWindowHours());
                                }
                                return chain.filter(exchange);
                            });
                })
                .onErrorResume(IllegalStateException.class,
                        e -> rejectKycExceeded(exchange, cfg.getWindowHours()))
                .onErrorResume(e -> {
                    // Redis or other infrastructure failure. Fail closed: reject with 503
                    // rather than allowing an unmetered KYC attempt through.
                    log.error("KYC rate limiter check failed (Redis unavailable?): {}", e.getMessage(), e);
                    return rejectServiceUnavailable(exchange);
                });
    }

    private Mono<Void> rejectServiceUnavailable(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        try {
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        } catch (UnsupportedOperationException ignored) {
            // Headers may already be sealed; body is still valid JSON
        }

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("RATE_LIMITER_UNAVAILABLE")
                .message("KYC service temporarily unavailable. Please try again in a moment.")
                .httpStatus(HttpStatus.SERVICE_UNAVAILABLE.value())
                .build();

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(ApiResponse.error(error));
        } catch (JsonProcessingException e) {
            body = "{\"success\":false,\"error\":{\"errorCode\":\"RATE_LIMITER_UNAVAILABLE\"}}"
                    .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> rejectKycExceeded(ServerWebExchange exchange, int windowHours) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        try {
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        } catch (UnsupportedOperationException ignored) {
            // Headers may already be sealed; body is still valid JSON
        }

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("KYC_RATE_LIMIT_EXCEEDED")
                .message("KYC document submission limit reached. Maximum "
                        + props.getKycRateLimiter().getMaxAttempts()
                        + " attempts allowed per " + windowHours + " hours.")
                .httpStatus(HttpStatus.TOO_MANY_REQUESTS.value())
                .build();

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(ApiResponse.error(error));
        } catch (JsonProcessingException e) {
            body = "{\"success\":false,\"error\":{\"errorCode\":\"KYC_RATE_LIMIT_EXCEEDED\"}}"
                    .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
