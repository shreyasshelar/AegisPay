package com.aegispay.gateway.config;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Disables Reactor Netty's built-in one-shot retry on connection errors.
 *
 * Without this, when a pooled (or new) TCP connection fails immediately after
 * being acquired, Reactor Netty transparently retries the request once on a
 * fresh connection. That second attempt reaches the downstream service and
 * returns 200, but the circuit-breaker has already committed a 503 fallback
 * response for the first failed attempt. Both paths then try to write to the
 * same ServerHttpResponse simultaneously, causing:
 *   ReadOnlyHttpHeaders.set() → UnsupportedOperationException
 *
 * Disabling the built-in retry means genuine connection errors surface
 * immediately to the circuit-breaker, which handles retry/fallback in a
 * single, controlled path.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClientCustomizer disableNettyRetry() {
        return httpClient -> httpClient.disableRetry(true);
    }
}
