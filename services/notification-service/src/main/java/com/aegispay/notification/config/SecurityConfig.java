package com.aegispay.notification.config;

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
                    // STOMP WebSocket handshake — auth is enforced by
                    // StompAuthChannelInterceptor on the CONNECT frame, not here.
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
     * Converts the scalar {@code aegispay_role} JWT claim to Spring Security authorities.
     *
     * <p>The Notification service is primarily event-driven (Kafka).  Its HTTP surface
     * exposes notification-history queries (CUSTOMER owns their own) and admin broadcast
     * endpoints.  The explicit lambda handles the scalar claim and defaults to
     * {@code ROLE_CUSTOMER} when the claim is absent (first-session social users).
     *
     * <p>WebSocket ({@code /ws/**}) is <em>not</em> secured here — the STOMP
     * {@code StompAuthChannelInterceptor} validates the Bearer token presented in the
     * CONNECT frame, which is the only point at which the JWT is available over WebSocket.
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
