package com.aegispay.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration loaded by all non-gateway services via
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 *
 * Registers the JWT extraction and header propagation filters as beans so
 * individual services get security behaviour without boilerplate configuration.
 *
 * The RLS tenant interceptor is wired separately in {@link TenantAutoConfig}
 * so that services without JDBC (e.g. notification-service) can load this
 * class without triggering a ClassNotFoundException on JdbcTemplate.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableAspectJAutoProxy
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
