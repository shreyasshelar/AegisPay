package com.aegispay.transaction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/actuator/prometheus",
                    "/actuator/metrics",
                    "/actuator/metrics/**",
                    "/actuator/circuitbreakers",
                    // WebSocket SockJS HTTP upgrade — token is inside the STOMP CONNECT
                    // frame, not in the HTTP Authorization header, so we cannot validate
                    // it here.  StompAuthInterceptor handles JWT auth at the STOMP layer.
                    "/ws/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    /**
     * Converts the {@code aegispay_role} JWT claim into Spring Security authorities.
     *
     * <p>The claim is a <em>scalar string</em> (e.g. {@code "CUSTOMER"}, {@code "ADMIN"}),
     * not a list.  {@link org.springframework.security.oauth2.server.resource.authentication
     * .JwtGrantedAuthoritiesConverter#setAuthoritiesClaimName} calls
     * {@code Jwt.getClaimAsStringList()} internally which can silently return an empty
     * collection for scalar strings in certain Spring Security versions.  We use an
     * explicit lambda to make behaviour unambiguous across all versions.
     *
     * <p>The {@code ROLE_} prefix is added so {@code @PreAuthorize("hasRole('ADMIN')")}
     * and {@code hasAnyRole(...)} expressions work without further configuration.
     *
     * <p>Social-login users (Google, Microsoft) will not have the {@code aegispay_role}
     * claim on their <em>first session</em> — Keycloak writes it back asynchronously
     * after the User Service registers them.  We default to {@code ROLE_CUSTOMER} so
     * customer-facing endpoints (send money, list transactions) still work; ADMIN-only
     * endpoints remain protected by their own {@code @PreAuthorize} annotations.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("aegispay_role");
            if (role == null || role.isBlank()) {
                // No aegispay_role claim — first-session social/federated user.
                // Treat as CUSTOMER so send-money and history endpoints work.
                return List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
            }
            return (Collection<GrantedAuthority>)
                    Collections.<GrantedAuthority>singletonList(
                            new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
        });
        return converter;
    }
}
