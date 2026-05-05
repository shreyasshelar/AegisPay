package com.aegispay.common.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-configuration that wires all observability filters for every
 * servlet-based service. The filter chain order ensures:
 *
 *   1. CorrelationIdFilter  (HIGHEST_PRECEDENCE)  — generate/accept correlation ID
 *   2. TraceParentFilter    (Order 1)              — extract W3C traceparent
 *   3. MdcLoggingFilter     (Order 2)              — bind request metadata to MDC
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AegisObservabilityAutoConfig {

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new CorrelationIdFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    public FilterRegistrationBean<TraceParentFilter> traceParentFilterRegistration() {
        FilterRegistrationBean<TraceParentFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TraceParentFilter());
        bean.setOrder(1);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    public FilterRegistrationBean<MdcLoggingFilter> mdcLoggingFilterRegistration() {
        FilterRegistrationBean<MdcLoggingFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new MdcLoggingFilter(serviceName));
        bean.setOrder(2);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    public SensitiveFieldMasker sensitiveFieldMasker() {
        return new SensitiveFieldMasker();
    }
}
