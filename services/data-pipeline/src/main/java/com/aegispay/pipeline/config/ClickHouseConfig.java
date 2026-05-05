package com.aegispay.pipeline.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * ClickHouse data-source configuration.
 *
 * <p>Uses {@link DriverManagerDataSource} (non-pooled) backed by the official
 * ClickHouse JDBC driver. Connection pooling is intentionally omitted here
 * because ClickHouse is write-heavy with infrequent, bulk inserts via the
 * scheduled sink — a simple driver-manager source is sufficient and avoids the
 * overhead and complexity of a connection pool that would mostly sit idle.
 *
 * <p>If higher write concurrency is required in future, replace with a
 * {@code HikariDataSource} using {@code maximumPoolSize=5}.
 */
@Slf4j
@Configuration
public class ClickHouseConfig {

    @Value("${clickhouse.url}")
    private String clickHouseUrl;

    @Value("${clickhouse.username}")
    private String clickHouseUsername;

    @Value("${clickhouse.password:}")
    private String clickHousePassword;

    /**
     * ClickHouse {@link DataSource} bean.
     *
     * <p>The ClickHouse JDBC driver class is {@code com.clickhouse.jdbc.ClickHouseDriver}
     * and is included via the {@code clickhouse-jdbc:all} classifier artifact.
     */
    @Bean
    public DataSource clickHouseDataSource() {
        log.info("Initialising ClickHouse data source: url={}", clickHouseUrl);
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        dataSource.setUrl(clickHouseUrl);
        dataSource.setUsername(clickHouseUsername);
        dataSource.setPassword(clickHousePassword);
        return dataSource;
    }

    /**
     * Named {@link JdbcTemplate} for ClickHouse writes.
     *
     * <p>Naming the bean {@code clickHouseJdbcTemplate} prevents Spring from
     * treating it as the primary data-source template and avoids conflicts with
     * any future relational data-source added to this service.
     */
    @Bean
    public JdbcTemplate clickHouseJdbcTemplate(DataSource clickHouseDataSource) {
        JdbcTemplate template = new JdbcTemplate(clickHouseDataSource);
        template.setQueryTimeout(30); // seconds
        return template;
    }
}
