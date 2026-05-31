package com.aegispay.gateway.config;

import com.aegispay.gateway.filter.CorrelationIdGatewayFilter;
import com.aegispay.gateway.filter.JwtRelayGatewayFilter;
import com.aegispay.gateway.filter.KycRateLimitGatewayFilter;
import com.aegispay.gateway.filter.TraceParentGatewayFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines all service routes programmatically so URIs come from config (not hardcoded YAML).
 *
 * Route order: most specific paths first, catch-alls last.
 *
 * Every authenticated route applies three gateway filters:
 *   1. CorrelationIdGatewayFilter  — generate/echo X-Correlation-ID
 *   2. TraceParentGatewayFilter    — generate/propagate W3C traceparent
 *   3. JwtRelayGatewayFilter       — add X-User-Id / X-User-Role headers from JWT
 *
 * The global rate-limiting filter (RateLimitingGlobalFilter) is a GlobalFilter
 * and runs automatically on every exchange — no per-route wiring needed.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GatewayRoutingConfig {

    private final GatewayProperties props;
    private final CorrelationIdGatewayFilter correlationIdFilter;
    private final TraceParentGatewayFilter traceParentFilter;
    private final JwtRelayGatewayFilter jwtRelayFilter;
    private final KycRateLimitGatewayFilter kycRateLimitFilter;

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        GatewayProperties.ServiceUris svc = props.getServices();

        return builder.routes()

            // ── User Service ─────────────────────────────────────────────────
            .route("user-service", r -> r
                .path("/api/v1/users/**")
                .filters(f -> f
                    .filter(correlationIdFilter)
                    .filter(traceParentFilter)
                    .filter(jwtRelayFilter)
                    .circuitBreaker(cb -> cb
                        .setName("user-service")
                        .setFallbackUri("forward:/fallback/service-unavailable"))
                    // GET-only retry: POSTs (/register, /kyc/documents) must not be
                    // retried at the gateway level — the X-Idempotency-Key header makes
                    // POST retries safe at the service level, but a gateway retry on a
                    // network timeout produces a second write that races with the first.
                    // 1 retry (2 total attempts) is sufficient; more amplify CB failures.
                    .retry(retryConfig -> retryConfig
                        .setRetries(1)
                        .setMethods(org.springframework.http.HttpMethod.GET)
                        .setStatuses(org.springframework.http.HttpStatus.BAD_GATEWAY,
                                     org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
                )
                .uri(svc.getUserService()))

            // ── Transaction Service ───────────────────────────────────────────
            .route("transaction-service", r -> r
                .path("/api/v1/transactions/**")
                .filters(f -> f
                    .filter(correlationIdFilter)
                    .filter(traceParentFilter)
                    .filter(jwtRelayFilter)
                    .circuitBreaker(cb -> cb
                        .setName("transaction-service")
                        .setFallbackUri("forward:/fallback/service-unavailable"))
                    // Only retry GET — POST retries cause idempotency key collision (409).
                    // 1 retry (2 total attempts) keeps failure amplification minimal.
                    .retry(retryConfig -> retryConfig
                        .setRetries(1)
                        .setMethods(org.springframework.http.HttpMethod.GET)
                        .setStatuses(
                            org.springframework.http.HttpStatus.BAD_GATEWAY,
                            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
                )
                .uri(svc.getTransactionService()))

            // ── Ledger Service ────────────────────────────────────────────────
            .route("ledger-service", r -> r
                .path("/api/v1/ledger/**")
                .filters(f -> f
                    .filter(correlationIdFilter)
                    .filter(traceParentFilter)
                    .filter(jwtRelayFilter)
                    .circuitBreaker(cb -> cb
                        .setName("ledger-service")
                        .setFallbackUri("forward:/fallback/service-unavailable"))
                )
                .uri(svc.getLedgerService()))

            // ── Payment Orchestrator ──────────────────────────────────────────
            .route("payment-orchestrator", r -> r
                .path("/api/v1/sagas/**")
                .filters(f -> f
                    .filter(correlationIdFilter)
                    .filter(traceParentFilter)
                    .filter(jwtRelayFilter)
                    .circuitBreaker(cb -> cb
                        .setName("payment-orchestrator")
                        .setFallbackUri("forward:/fallback/service-unavailable"))
                )
                .uri(svc.getOrchestratorService()))

            // ── Risk Engine ───────────────────────────────────────────────────
            .route("risk-engine", r -> r
                .path("/api/v1/risk/**")
                .filters(f -> f
                    .filter(correlationIdFilter)
                    .filter(traceParentFilter)
                    .filter(jwtRelayFilter)
                    .circuitBreaker(cb -> cb
                        .setName("risk-engine")
                        .setFallbackUri("forward:/fallback/service-unavailable"))
                )
                .uri(svc.getRiskEngine()))

            // ── Notification Service — REST ───────────────────────────────────
            .route("notification-service-rest", r -> r
                .path("/api/v1/notifications/**")
                .filters(f -> f
                    .filter(correlationIdFilter)
                    .filter(traceParentFilter)
                    .filter(jwtRelayFilter)
                    .circuitBreaker(cb -> cb
                        .setName("notification-service")
                        .setFallbackUri("forward:/fallback/service-unavailable"))
                )
                .uri(svc.getNotificationService()))

            // ── Notification Service — WebSocket upgrade ──────────────────────
            // WebSocket upgrades cannot use the standard circuit-breaker filter
            // (it breaks the Upgrade handshake). The CB on the REST route above
            // provides health signal; WS reconnects are handled client-side.
            .route("notification-service-ws", r -> r
                .path("/ws/**")
                .filters(f -> f
                    .filter(correlationIdFilter)
                    .filter(traceParentFilter)
                    .filter(jwtRelayFilter)
                )
                .uri(svc.getNotificationService()))

            // ── AI Platform — KYC (rate-limited: 5 attempts per 24 h per user) ─
            // Uses a DEDICATED circuit breaker ("kyc-circuit-breaker") with a
            // 360 s TimeLimiter — the KYC pipeline has 4 sequential AI vision
            // calls that can each take up to 90 s on OpenRouter free tier.
            // Sharing the 120 s "ai-platform" CB would time out mid-pipeline.
            .route("ai-platform-kyc", r -> r
                .path("/api/v1/ai/kyc/**")
                .filters(f -> f
                    .filter(correlationIdFilter)
                    .filter(traceParentFilter)
                    .filter(jwtRelayFilter)
                    .filter(kycRateLimitFilter)          // ← KYC-specific limiter
                    .circuitBreaker(cb -> cb
                        .setName("kyc-circuit-breaker")
                        .setFallbackUri("forward:/fallback/service-unavailable"))
                )
                .uri(svc.getAiPlatform()))

            // ── AI Platform — all other AI routes (incidents, errors, fraud) ──
            .route("ai-platform", r -> r
                .path("/api/v1/ai/**")
                .filters(f -> f
                    .filter(correlationIdFilter)
                    .filter(traceParentFilter)
                    .filter(jwtRelayFilter)
                    .circuitBreaker(cb -> cb
                        .setName("ai-platform")
                        .setFallbackUri("forward:/fallback/service-unavailable"))
                )
                .uri(svc.getAiPlatform()))

            .build();
    }
}
