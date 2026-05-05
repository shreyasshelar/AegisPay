package com.aegispay.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerReactiveAuthenticationManagerResolver;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

/**
 * WebFlux security configuration for the API gateway.
 *
 * Multi-IdP JWT support is implemented via JwtIssuerReactiveAuthenticationManagerResolver,
 * which creates a separate NimbusReactiveJwtDecoder per issuer and caches them.
 * Adding a new IdP requires only a new entry in aegispay.gateway.oauth2-trusted-issuers.
 *
 * Public paths (health, metrics) bypass auth so Kubernetes probes and Prometheus
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
     * Resolves the correct ReactiveAuthenticationManager for each JWT by inspecting
     * the `iss` (issuer) claim, then dispatching to the matching NimbusReactiveJwtDecoder.
     * Each decoder is lazily loaded from the issuer's JWKS endpoint and cached.
     */
    @Bean
    public ReactiveAuthenticationManagerResolver<ServerWebExchange> authenticationManagerResolver() {
        List<String> issuers = gatewayProperties.getOauth2TrustedIssuers();

        if (issuers.isEmpty()) {
            log.warn("No OAuth2 trusted issuers configured — all authenticated requests will be rejected");
        } else {
            log.info("Configured {} trusted OAuth2 issuer(s): {}", issuers.size(), issuers);
        }

        return new JwtIssuerReactiveAuthenticationManagerResolver(issuers);
    }
}
