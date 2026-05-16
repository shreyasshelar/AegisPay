package com.aegispay.gateway.filter;

import com.aegispay.gateway.config.GatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitingGlobalFilterTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private GatewayProperties props;
    private RateLimitingGlobalFilter filter;

    @BeforeEach
    void setUp() {
        props = new GatewayProperties();
        props.getRateLimiter().setMaxRequests(5);
        props.getRateLimiter().setWindowSeconds(60);
        props.getRateLimiter().setKeyTtlSeconds(65);
        props.getRateLimiter().setLimitHeader("X-RateLimit-Limit");
        props.getRateLimiter().setRemainingHeader("X-RateLimit-Remaining");
        props.getRateLimiter().setResetHeader("X-RateLimit-Reset");

        filter = new RateLimitingGlobalFilter(redisTemplate, props, new ObjectMapper());

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void allowsRequestWhenUnderLimit() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(Boolean.TRUE));

        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());

        filter.filter(exchange, exch -> {
            assertThat(exch.getResponse().getStatusCode()).isNull();
            return Mono.empty();
        }).block();
    }

    @Test
    void rejectsWith429WhenLimitExceeded() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(6L)); // over limit of 5
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(Boolean.FALSE));

        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());

        filter.filter(exchange, exch -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("5");
    }

    @Test
    void setsRateLimitHeaders() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(3L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(Boolean.FALSE));

        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());

        filter.filter(exchange, exch -> Mono.empty()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("2");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Reset")).isNotNull();
    }

    @Test
    void bypassesActuatorPaths() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build());

        // No Redis calls expected — chain passes through immediately
        filter.filter(exchange, exch -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void hasCorrectOrder() {
        assertThat(filter.getOrder()).isEqualTo(-170);
    }
}
