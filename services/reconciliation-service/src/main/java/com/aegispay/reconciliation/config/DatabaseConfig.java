package com.aegispay.reconciliation.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Explicit primary DataSource config — required because ClickHouseConfig also
 * declares a DataSource bean, which causes Spring Boot to back off its auto-
 * configuration. We re-declare it here with @Primary so Hibernate / JPA uses
 * PostgreSQL, not ClickHouse.
 */
@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource dataSource() {
        return primaryDataSourceProperties().initializeDataSourceBuilder().build();
    }
}
