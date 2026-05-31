package com.aegispay.user.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Security configuration for the User Service resource server.
 *
 * <h3>Cold-start ECONNRESET fix (Bug #5 — same class as Bug #4 in AI Platform)</h3>
 *
 * <p>Spring Boot's default {@link NimbusJwtDecoder} for {@code jwk-set-uri} fetches the
 * Keycloak JWK set <em>lazily</em> on the first {@code decode()} call using an
 * auto-configured {@link RestTemplate} that has <strong>zero connect timeout and zero
 * read timeout</strong> (OS/JVM default = effectively infinite).
 *
 * <p>After every {@code start-aegispay.bat} restart, {@code docker compose down -v} wipes
 * all volumes, so Keycloak starts completely fresh and regenerates its realm signing keys.
 * The very first JWT-authenticated request to User Service — which can be:
 * <ul>
 *   <li>{@code POST /register} called by {@code auth.ts} on initial sign-in,</li>
 *   <li>{@code GET /me} called by {@code UserLookupService} in AI Platform when the
 *       user's JWT lacks the {@code aegispay_user_id} claim (first social-login session),</li>
 *   <li>any other JWT-authenticated endpoint,</li>
 * </ul>
 * triggers a blocking HTTP GET to Keycloak's JWK endpoint.  If Keycloak is still warming
 * up at that moment, the call hangs indefinitely (zero timeout), blocking the Tomcat
 * thread past the upstream service's read timeout and producing a cascading failure
 * visible as ECONNRESET or "Upload failed" in the browser.
 *
 * <p><b>Fix:</b>
 * <ol>
 *   <li>Supply a custom {@link NimbusJwtDecoder} bean whose internal {@link RestTemplate}
 *       has explicit connect and read timeouts, so a slow Keycloak produces a fast 401
 *       instead of an indefinite hang.</li>
 *   <li>Add an {@link ApplicationRunner} that eagerly pre-fetches the JWK set at startup
 *       (where blocking I/O is acceptable), so the first inbound JWT request uses the
 *       already-cached public keys with zero network overhead.</li>
 * </ol>
 *
 * <p>This mirrors the eager-pre-warm approach already applied to the API Gateway
 * ({@code Bug #2}) and AI Platform ({@code Bug #4}).
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Connect timeout (ms) for the HTTP call that fetches the Keycloak JWK set.
     * 10 s is generous for localhost; fail-fast if Keycloak is unreachable.
     */
    private static final int JWK_CONNECT_TIMEOUT_MS = 10_000;

    /**
     * Read/socket timeout (ms) for the JWK set HTTP response.
     * 30 s matches the browser's Axios timeout — ensures a stuck JWK fetch surfaces
     * as a 401 rather than hanging the Tomcat thread past the client's deadline.
     */
    private static final int JWK_READ_TIMEOUT_MS = 30_000;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    /**
     * Handles internal service-to-service calls authenticated via X-Internal-Api-Key.
     * Must run BEFORE BearerTokenAuthenticationFilter so an already-set Authentication
     * (with ROLE_ADMIN) is seen by the JWT filter — which then skips processing when
     * no Bearer token is present in the request.
     */
    private final InternalApiKeyFilter internalApiKeyFilter;

    // ── Security filter chain ─────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // InternalApiKeyFilter runs before the JWT filter so that service-to-service
            // calls with X-Internal-Api-Key bypass Keycloak JWT validation entirely.
            .addFilterBefore(internalApiKeyFilter, BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/actuator/prometheus"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    // Wire the explicitly-configured decoder so the auto-configured
                    // (no-timeout) decoder is never registered.
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
        return http.build();
    }

    // ── JWT decoder with explicit timeouts ────────────────────────────────────

    /**
     * Custom {@link NimbusJwtDecoder} whose {@link RestTemplate} carries explicit
     * connect and read timeouts.
     *
     * <p>Replaces Spring Boot's zero-timeout auto-configured decoder.
     * Pre-warmed by {@link #jwkSetPreWarmer(JwtDecoder)}.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(JWK_CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(JWK_READ_TIMEOUT_MS);

        return NimbusJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .restOperations(new RestTemplate(factory))
                .build();
    }

    // ── Eager JWK pre-warm ────────────────────────────────────────────────────

    /**
     * Eagerly pre-fetches the Keycloak JWK set during application startup so the
     * first inbound JWT-authenticated request does not pay the latency cost of a
     * cold JWK fetch.
     *
     * <h3>How it works</h3>
     * {@link NimbusJwtDecoder#decode} fetches and caches the JWK set <em>before</em>
     * attempting signature verification. We call it with a syntactically valid but
     * deliberately invalid JWT: the JWK set is fetched and stored in Nimbus's
     * {@code DefaultJWKSetCache}, then a {@code JwtException} is thrown because the
     * dummy signature cannot be verified. That exception is expected and benign.
     *
     * <p>On the first real request the decoder finds the JWK set already cached and
     * proceeds to signature verification with zero network I/O.
     *
     * <p>If Keycloak is unreachable at startup, the warning is logged and the decoder
     * falls back to lazy initialization — the explicit timeouts on {@link #jwtDecoder()}
     * then prevent an indefinite hang.
     */
    @Bean
    public ApplicationRunner jwkSetPreWarmer(JwtDecoder jwtDecoder) {
        return args -> {
            log.info("Pre-warming User Service JWK decoder: {}", jwkSetUri);
            try {
                // Header = {"alg":"RS256"}, Payload = {"sub":"warmup"}, Signature = "foo"
                // (base64url-valid compact serialization; invalid signature is expected).
                // NimbusJwtDecoder fetches + caches the JWK set before verifying — so the
                // cache is populated even though decode() ultimately throws JwtException.
                jwtDecoder.decode(
                    "eyJhbGciOiJSUzI1NiJ9"
                    + ".eyJzdWIiOiJ3YXJtdXAifQ"
                    + ".Zm9v"
                );
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                boolean connectFailure = msg.contains("connection") || msg.contains("timed out")
                        || msg.contains("refused") || msg.contains("read timed out");
                if (connectFailure) {
                    log.warn("JWK pre-warm could not reach '{}' — lazy init active, "
                            + "explicit timeouts guard against hangs: {}",
                            jwkSetUri, e.getMessage());
                } else {
                    // "Failed to validate" / "Bad signature" / "Unable to find a suitable key"
                    // all confirm the JWK set was fetched and the cache is warm.
                    log.info("JWK set pre-warmed for User Service (validation failure on "
                            + "dummy token is expected — issuer: {})", jwkSetUri);
                }
            }
        };
    }

    // ── JWT → Spring Security authority converter ─────────────────────────────

    /**
     * Converts the scalar {@code aegispay_role} JWT claim into Spring Security authorities.
     *
     * <p>The claim is a <em>scalar string</em> (e.g. {@code "CUSTOMER"}, {@code "ADMIN"}),
     * not a list. Spring's built-in {@code JwtGrantedAuthoritiesConverter} with
     * {@code setAuthoritiesClaimName} calls {@code Jwt.getClaimAsStringList()} which works
     * for both scalars and lists — but we provide an explicit lambda here to make the
     * behaviour unambiguous and avoid any version-dependent edge cases.
     *
     * <p>The {@code ROLE_} prefix is added so {@code @PreAuthorize("hasRole('ADMIN')")}
     * and {@code hasAnyRole(...)} expressions work without further configuration.
     *
     * <p>No {@code aegispay_role} claim (e.g. first-session social user whose Keycloak
     * attributes haven't been written yet): defaults to {@code ROLE_CUSTOMER} so
     * self-service endpoints still work (they use SpEL subject checks, not role checks).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("aegispay_role");
            if (role == null || role.isBlank()) {
                return List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
            }
            return (Collection<GrantedAuthority>)
                    Collections.<GrantedAuthority>singletonList(
                            new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
        });
        return converter;
    }
}
