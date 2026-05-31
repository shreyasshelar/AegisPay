package com.aegispay.transaction.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableJpaAuditing
@EnableConfigurationProperties(TransactionServiceProperties.class)
@EnableMongoRepositories(basePackages = "com.aegispay.transaction.readmodel")
public class AppConfig {}
