package com.aegispay.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Production-grade circuit breaker configuration for the API Gateway.
 *
 * <h3>Why programmatic instead of YAML?</h3>
 * <ul>
 *   <li>The {@code spring.cloud.circuitbreaker.resilience4j.instances} YAML namespace
 *       does NOT expose {@code recordException}. The programmatic
 *       {@link Customizer} is the only way to restrict which exception types count
 *       as CB failures.</li>
 *   <li>A {@link Customizer} bean is applied AFTER YAML auto-configuration and
 *       <em>replaces</em> per-instance settings wholesale (the builder always starts
 *       from the Resilience4j default, not from the YAML values). Consolidating here
 *       avoids a misleading dual-source setup where YAML values silently have no effect.</li>
 * </ul>
 *
 * <h3>Exception policy — why not restrict to {@code IOException | TimeoutException}?</h3>
 * In the Gateway's reactive pipeline, HTTP 4xx/5xx responses from downstream services
 * complete the {@code Mono} normally — they are NOT exceptions and can never open a
 * circuit regardless of the predicate. The only exceptions that reach Resilience4j are
 * genuine network failures:
 * <ul>
 *   <li>{@link java.net.ConnectException} — service unreachable (subtype of IOException)</li>
 *   <li>{@code io.netty.handler.timeout.ReadTimeoutException} — response timeout
 *       (Netty-specific, does NOT extend {@link TimeoutException})</li>
 * </ul>
 * The predicate below captures all of these:
 * IOException subtypes + java TimeoutException + any class whose simple name ends
 * in "TimeoutException" (covers all Netty timeout variants without a hard Netty import).
 *
 * <h3>Key parameters (all services except ai-platform)</h3>
 * <ul>
 *   <li>{@code minimumNumberOfCalls=20} — the CB won't evaluate failure rate until 20 calls
 *       have completed. Prevents the circuit from opening during service startup when a
 *       handful of connection-refused errors would previously cross the 50–60% threshold
 *       on a window of only 5 calls.</li>
 *   <li>{@code slowCallRateThreshold=100} — slow calls NEVER count toward opening the CB.
 *       Long LLM and Stripe calls must not be misread as failures.</li>
 *   <li>{@code waitDurationInOpenState=30s} — gives restarting services adequate time to
 *       become healthy before the CB probes again.</li>
 *   <li>{@code automaticTransitionFromOpenToHalfOpenEnabled=true} — the CB transitions to
 *       HALF_OPEN automatically after {@code waitDuration}, so recovery does not depend on
 *       the next real user request triggering the probe.</li>
 * </ul>
 */
@Configuration
public class CircuitBreakerCustomizerConfig {

    // ── Exception predicate ───────────────────────────────────────────────────────
    /**
     * Records a CB failure for:
     * <ul>
     *   <li>{@link IOException} and all subclasses (ConnectException, SocketException, …)</li>
     *   <li>{@link TimeoutException} (java.util.concurrent)</li>
     *   <li>Any Netty timeout variant: ReadTimeoutException, WriteTimeoutException, etc.
     *       These do not extend java's TimeoutException so they need the name check.</li>
     * </ul>
     * All other throwables (codec errors, NPEs, etc.) do NOT open the circuit — if those
     * reach this layer something is catastrophically wrong at the gateway level, not the
     * downstream service.
     */
    private static final Predicate<Throwable> NETWORK_FAILURE = ex ->
            ex instanceof IOException
            || ex instanceof TimeoutException
            || ex.getClass().getSimpleName().endsWith("TimeoutException");

    // ── Shared config builders ────────────────────────────────────────────────────

    /**
     * Build a {@link CircuitBreakerConfig} with the shared production policy applied.
     * {@code slowCallRateThreshold} is always 100 (slow calls never open the CB).
     * {@code automaticTransitionFromOpenToHalfOpenEnabled} is always true.
     */
    private static CircuitBreakerConfig buildConfig(
            int slidingWindow, int minCalls, float failureRate,
            Duration waitOpen, int halfOpenCalls, Duration slowCallThreshold) {

        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindow)
                .minimumNumberOfCalls(minCalls)
                .failureRateThreshold(failureRate)
                .waitDurationInOpenState(waitOpen)
                .permittedNumberOfCallsInHalfOpenState(halfOpenCalls)
                .slowCallDurationThreshold(slowCallThreshold)
                // Slow calls NEVER count toward failure rate — prevents LLM / Stripe latency
                // from being misread as service failure.
                .slowCallRateThreshold(100f)
                // Probe HALF_OPEN automatically — recovery must not depend on a real user
                // request happening to arrive exactly at waitDuration expiry.
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                // Only true network errors open the circuit (see Javadoc above).
                .recordException(NETWORK_FAILURE)
                .build();
    }

    /**
     * Build a {@link TimeLimiterConfig} for the given per-route timeout.
     *
     * <p><b>Why this matters:</b> Resilience4j's default {@code TimeLimiterConfig}
     * has a {@code timeoutDuration} of <em>1 second</em>.  Without an explicit override
     * every downstream call that takes longer than 1 s (cold TCP connections, Stripe API
     * calls, ML inference, …) fires the TimeLimiter, which:
     * <ol>
     *   <li>Immediately invokes the circuit-breaker fallback → client sees 503.</li>
     *   <li>Records a {@code TimeoutException} as a CB failure.</li>
     *   <li>Does <em>not</em> cancel the in-flight Netty request
     *       ({@code cancelRunningFuture=false} for financial routes), so the downstream
     *       service commits its DB transaction — leaving the client with a 503 but the
     *       backend in a succeeded state (e.g. balance credited but UI shows error).</li>
     * </ol>
     *
     * <p>Setting {@code cancelRunningFuture(false)} on financial routes (ledger, orchestrator)
     * means: if the timeout DOES fire (extreme latency spike), the Stripe/DB work still
     * completes correctly — no partial writes.  Non-financial routes use the default
     * {@code true} so hung connections are cleaned up promptly.
     */
    private static TimeLimiterConfig timeLimiter(Duration timeout, boolean cancelRunning) {
        return TimeLimiterConfig.custom()
                .timeoutDuration(timeout)
                .cancelRunningFuture(cancelRunning)
                .build();
    }

    // ── Factory customizer ────────────────────────────────────────────────────────

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> gatewayCircuitBreakerCustomizer() {
        return factory -> {

            // ── user-service ─────────────────────────────────────────────────────
            // Handles /me, /register, /kyc — registration spikes on launch, so a
            // larger window (20) prevents startup noise from locking users out.
            // failureRate 60 % — tolerate short auth-service blips.
            // TimeLimiter 10 s: covers cold JVM start + DB connection pool warmup.
            factory.configure(builder -> builder
                    .circuitBreakerConfig(buildConfig(
                            20, 20, 60f,
                            Duration.ofSeconds(30), 5,
                            Duration.ofSeconds(8)))
                    .timeLimiterConfig(timeLimiter(Duration.ofSeconds(10), true)),
                    "user-service");

            // ── transaction-service ───────────────────────────────────────────────
            // Payment-critical; identical window to user-service for consistency.
            // TimeLimiter 10 s: transaction creation is a DB write — should be fast,
            // but allow headroom for cold-start JPA pool initialization.
            factory.configure(builder -> builder
                    .circuitBreakerConfig(buildConfig(
                            20, 20, 60f,
                            Duration.ofSeconds(30), 5,
                            Duration.ofSeconds(10)))
                    .timeLimiterConfig(timeLimiter(Duration.ofSeconds(10), true)),
                    "transaction-service");

            // ── ledger-service ────────────────────────────────────────────────────
            // Balance-critical; strict 50 % threshold.
            // TimeLimiter 20 s: the /topup/confirm endpoint calls Stripe API with a
            // 15 s read timeout (set in TopUpService).  The gateway must wait at least
            // as long; we add 5 s headroom for DB writes and response serialization.
            // cancelRunningFuture=false: if the 20 s budget is ever exhausted, let the
            // ledger transaction commit so money is never lost.
            factory.configure(builder -> builder
                    .circuitBreakerConfig(buildConfig(
                            20, 20, 50f,
                            Duration.ofSeconds(30), 5,
                            Duration.ofSeconds(5)))
                    .timeLimiterConfig(timeLimiter(Duration.ofSeconds(20), false)),
                    "ledger-service");

            // ── payment-orchestrator ──────────────────────────────────────────────
            // Saga coordinator; Stripe calls can take 10-15 s.
            // TimeLimiter 20 s: same reasoning as ledger-service.
            // cancelRunningFuture=false: saga must be allowed to complete so
            // compensating transactions don't orphan.
            factory.configure(builder -> builder
                    .circuitBreakerConfig(buildConfig(
                            20, 20, 50f,
                            Duration.ofSeconds(30), 5,
                            Duration.ofSeconds(15)))
                    .timeLimiterConfig(timeLimiter(Duration.ofSeconds(20), false)),
                    "payment-orchestrator");

            // ── risk-engine ───────────────────────────────────────────────────────
            // ML inference can be slow; 15 s covers the rule engine + RAG retrieval.
            factory.configure(builder -> builder
                    .circuitBreakerConfig(buildConfig(
                            20, 20, 50f,
                            Duration.ofSeconds(30), 5,
                            Duration.ofSeconds(8)))
                    .timeLimiterConfig(timeLimiter(Duration.ofSeconds(15), true)),
                    "risk-engine");

            // ── notification-service ──────────────────────────────────────────────
            // Best-effort delivery; higher failure tolerance (60 %) and a shorter
            // wait (15 s) so transient outages don't block the send-money flow.
            factory.configure(builder -> builder
                    .circuitBreakerConfig(buildConfig(
                            20, 20, 60f,
                            Duration.ofSeconds(15), 5,
                            Duration.ofSeconds(5)))
                    .timeLimiterConfig(timeLimiter(Duration.ofSeconds(10), true)),
                    "notification-service");

            // ── ai-platform (non-KYC: error resolution, fraud copilot, triage) ──────
            // Single LLM calls take 30-90 s on OpenRouter free tier.
            // TimeLimiter 120 s: sufficient for one LLM call + small overhead.
            // failureRate 80 % — AI returns 200 + graceful-degraded payload on most
            // internal errors so true 5xx are rare; require 80 % to open.
            // slowCallThreshold 90 s — only completely hung calls count as slow.
            factory.configure(builder -> builder
                    .circuitBreakerConfig(
                            CircuitBreakerConfig.custom()
                                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                                    .slidingWindowSize(20)
                                    .minimumNumberOfCalls(10)
                                    .failureRateThreshold(80f)
                                    .waitDurationInOpenState(Duration.ofSeconds(30))
                                    .permittedNumberOfCallsInHalfOpenState(3)
                                    .slowCallDurationThreshold(Duration.ofSeconds(90))
                                    .slowCallRateThreshold(100f)
                                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                                    .recordException(NETWORK_FAILURE)
                                    .build())
                    .timeLimiterConfig(timeLimiter(Duration.ofSeconds(120), true)),
                    "ai-platform");

            // ── kyc-circuit-breaker (dedicated for KYC vision pipeline) ──────────
            // KYC runs 4 sequential AI vision calls (quality → tampering →
            // validation → OCR).  Each call can take 30-90 s on OpenRouter free tier.
            // Worst-case total: 4 × 90 s = 360 s.  This dedicated circuit breaker
            // gives the KYC route its own generous TimeLimiter so it doesn't share
            // the 120 s budget with regular LLM calls on the ai-platform CB.
            //
            // TimeLimiter 360 s (6 min): covers the absolute worst case of 4 slow calls.
            // failureRate 80 %: high tolerance — vision timeouts are common on the
            //   free tier, and we don't want a few slow requests to lock out legitimate
            //   submissions.
            // waitOpen 60 s: longer cooldown so a genuinely-overloaded vision API
            //   has time to recover before the CB probes again.
            // cancelRunningFuture true: if the 360 s budget fires, cancel cleanly —
            //   KYC is idempotent (user can re-upload) unlike financial transactions.
            factory.configure(builder -> builder
                    .circuitBreakerConfig(
                            CircuitBreakerConfig.custom()
                                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                                    .slidingWindowSize(10)
                                    .minimumNumberOfCalls(3)
                                    .failureRateThreshold(80f)
                                    .waitDurationInOpenState(Duration.ofSeconds(60))
                                    .permittedNumberOfCallsInHalfOpenState(2)
                                    .slowCallDurationThreshold(Duration.ofSeconds(300))
                                    .slowCallRateThreshold(100f)
                                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                                    .recordException(NETWORK_FAILURE)
                                    .build())
                    .timeLimiterConfig(timeLimiter(Duration.ofSeconds(360), true)),
                    "kyc-circuit-breaker");

            // ── default ───────────────────────────────────────────────────────────
            // Any future route that adds a named CB without an explicit entry above
            // inherits safe defaults — prevents accidental minimumNumberOfCalls=100
            // (the Resilience4j hard-coded default that effectively disables the CB
            // on low-traffic routes).
            factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(buildConfig(
                            20, 20, 50f,
                            Duration.ofSeconds(30), 5,
                            Duration.ofSeconds(10)))
                    .timeLimiterConfig(timeLimiter(Duration.ofSeconds(10), true))
                    .build());
        };
    }
}
