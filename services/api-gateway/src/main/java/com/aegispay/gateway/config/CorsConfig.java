package com.aegispay.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Global CORS policy applied at the gateway level.
 * Individual downstream services do NOT need their own CORS config
 * because the gateway always mediates external traffic.
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final GatewayProperties props;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        GatewayProperties.Cors corsProps = props.getCors();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(corsProps.getAllowedOrigins());
        config.setAllowedMethods(corsProps.getAllowedMethods());
        config.setAllowedHeaders(corsProps.getAllowedHeaders());
        config.setAllowCredentials(corsProps.isAllowCredentials());
        config.setMaxAge(corsProps.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
