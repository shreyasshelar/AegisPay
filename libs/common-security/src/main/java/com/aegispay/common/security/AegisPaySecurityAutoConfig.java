package com.aegispay.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration loaded by all non-gateway services via
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 *
 * Registers the JWT extraction and header propagation filters as beans so
 * individual services get security behaviour without boilerplate configuration.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(JwtClaimsExtractor.class)
public class AegisPaySecurityAutoConfig {

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtClaimsExtractor extractor) {
        return new JwtAuthenticationFilter(extractor);
    }

    @Bean
    public SecurityHeaderFilter securityHeaderFilter() {
        return new SecurityHeaderFilter();
    }
}
