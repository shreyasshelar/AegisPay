package com.aegispay.gateway.config;

import com.nimbusds.jwt.JWTParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerReactiveAuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WebFlux security configuration for the API gateway.
 *
 * <p>Multi-IdP JWT support: one {@link JwtReactiveAuthenticationManager} is built per
 * distinct trusted issuer. Adding a new IdP requires only a new entry in
 * {@code aegispay.gateway.oauth2-trusted-issuers}.
 *
 * <p>Public paths (health, metrics) bypass auth so Kubernetes probes and Prometheus
 * scraping work without a JWT.
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info",
        "/actuator/prometheus",
        "/actuator/metrics",
        "/actuator/metrics/**",
        "/actuator/circuitbreakers",
    };

    private final GatewayProperties gatewayProperties;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            ReactiveAuthenticationManagerResolver<ServerWebExchange> authManagerResolver) {

        return http
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(HttpMethod.OPTIONS).permitAll()           // CORS preflight
                .pathMatchers(PUBLIC_PATHS).permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationManagerResolver(authManagerResolver)
            )
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .headers(headers -> headers
                .contentTypeOptions(ServerHttpSecurity.HeaderSpec.ContentTypeOptionsSpec::disable)
                .frameOptions(ServerHttpSecurity.HeaderSpec.FrameOptionsSpec::disable)
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            .build();
    }

    /**
     * Resolves the correct {@link ReactiveAuthenticationManager} for each inbound JWT.
     *
     * <h3>Cold-start ECONNRESET fix</h3>
     * The stock {@link JwtIssuerReactiveAuthenticationManagerResolver} initialises a
     * {@code NimbusReactiveJwtDecoder} <em>lazily</em> on the first JWT request by calling
     * {@code ReactiveJwtDecoders.fromOidcIssuerLocation()} inside a
     * {@code Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())}.
     * When that blocking OIDC-discovery HTTP call is slow (Keycloak still warming up after
     * a fresh {@code start-aegispay.bat} run), the API-Gateway circuit-breaker TimeLimiter
     * fires and tears down the TCP connection → Next.js proxy sees {@code ECONNRESET}.
     *
     * <p>Fix: call {@code ReactiveJwtDecoders.fromOidcIssuerLocation()} <strong>eagerly</strong>
     * here, at {@code @Bean}-construction time, on the Spring container thread where blocking
     * I/O is acceptable.  The resulting decoder is stored in {@code eagerManagers}.  Every
     * subsequent JWT request looks up the pre-built manager in O(1) with no I/O.
     *
     * <p>Issuers unreachable at startup (placeholder Okta/Entra domains in local dev) are
     * logged as warnings and handed to a lazy-fallback resolver so they never hard-fail the
     * application context.
     */
    @Bean
    public ReactiveAuthenticationManagerResolver<ServerWebExchange> authenticationManagerResolver() {
        // Deduplicate while preserving declaration order
        List<String> issuers = gatewayProperties.getOauth2TrustedIssuers()
                .stream().distinct().toList();

        if (issuers.isEmpty()) {
            log.warn("No OAuth2 trusted issuers configured — all authenticated requests will be rejected");
        } else {
            log.info("Building JWT decoders for {} trusted OAuth2 issuer(s): {}", issuers.size(), issuers);
        }

        // ── Eager build: Spring container thread — blocking OIDC discovery is fine here ──
        Map<String, ReactiveAuthenticationManager> eagerManagers = new LinkedHashMap<>();
        for (String issuer : issuers) {
            if (eagerManagers.containsKey(issuer)) {
                continue; // skip duplicates already built
            }
            try {
                ReactiveJwtDecoder decoder = ReactiveJwtDecoders.fromOidcIssuerLocation(issuer);
                eagerManagers.put(issuer, new JwtReactiveAuthenticationManager(decoder));
                log.info("Pre-warmed JWT decoder for issuer: {}", issuer);
            } catch (Exception e) {
                // External IdPs (Entra, Okta) may be unreachable in local dev; warn, don't fail
                log.warn("JWT decoder pre-warm failed for '{}' (falling back to lazy init): {}",
                        issuer, e.getMessage());
            }
        }

        // ── Lazy fallback for issuers that failed pre-warm ────────────────────────────────
        // Pass the full issuers list so it can validate the `iss` claim against all
        // configured issuers (not just the ones that failed to pre-warm).
        JwtIssuerReactiveAuthenticationManagerResolver lazyFallback =
                new JwtIssuerReactiveAuthenticationManagerResolver(issuers);

        return exchange -> {
            // Fast path: decode iss claim from JWT payload bytes — zero I/O
            String issuer = extractIssuerClaim(exchange);
            if (issuer != null && eagerManagers.containsKey(issuer)) {
                return Mono.just(eagerManagers.get(issuer));
            }
            // Slow path: issuer not pre-warmed (e.g., first hit for an external IdP)
            return lazyFallback.resolve(exchange);
        };
    }

    /**
     * Extracts the {@code iss} claim from the Bearer JWT in the Authorization header.
     * Uses Nimbus {@link JWTParser} to decode the header+payload without signature
     * verification — CPU-only, no network calls.
     *
     * @return the issuer string, or {@code null} if the header is absent or the token
     *         is malformed (the downstream resolver will then reject it with 401)
     */
    private String extractIssuerClaim(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            return JWTParser.parse(authHeader.substring(7)).getJWTClaimsSet().getIssuer();
        } catch (Exception e) {
            // Malformed token — let the fallback resolver reject it with 401
            return null;
        }
    }
}
