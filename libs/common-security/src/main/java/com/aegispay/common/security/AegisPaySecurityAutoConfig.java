package com.aegispay.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auto-configuration loaded by all non-gateway services via
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 *
 * Registers the JWT extraction and header propagation filters as beans so
 * individual services get security behaviour without boilerplate configuration.
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

    /**
     * Wires up the RLS tenant interceptor only when a {@link JdbcTemplate} is on
     * the classpath and configured — i.e. in services that talk to PostgreSQL.
     * Gateway and pure-messaging services don't expose a JdbcTemplate and are
     * therefore unaffected.
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    public TenantTransactionInterceptor tenantTransactionInterceptor(JdbcTemplate jdbcTemplate) {
        return new TenantTransactionInterceptor(jdbcTemplate);
    }
}
