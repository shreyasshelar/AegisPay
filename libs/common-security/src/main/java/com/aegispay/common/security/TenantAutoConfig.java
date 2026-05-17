package com.aegispay.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Separate auto-configuration for the RLS tenant interceptor.
 *
 * Kept in its own class so that {@link AegisPaySecurityAutoConfig} (which is
 * loaded by every service) never imports JdbcTemplate at the Java class level.
 * Services without a DataSource (e.g. notification-service) have no JdbcTemplate
 * on the classpath — if this class were inlined, Spring's introspection would
 * throw ClassNotFoundException before the @ConditionalOnBean guard could fire.
 *
 * Spring Boot evaluates @ConditionalOnClass from bytecode metadata (ASM) before
 * loading this class, so the condition check is safe even when spring-jdbc is absent.
 */
@AutoConfiguration(after = AegisPaySecurityAutoConfig.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
public class TenantAutoConfig {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    public TenantTransactionInterceptor tenantTransactionInterceptor(JdbcTemplate jdbcTemplate) {
        return new TenantTransactionInterceptor(jdbcTemplate);
    }
}
