package com.aegispay.pipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the AegisPay Data Pipeline service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Consume Kafka topics: transaction.completed, transaction.failed,
 *       transaction.rolled-back, risk.assessed</li>
 *   <li>Apply Kafka Streams transformations and windowed aggregations</li>
 *   <li>Persist analytics records to ClickHouse via JDBC</li>
 *   <li>Expose health and Prometheus metrics endpoints</li>
 * </ul>
 */
@SpringBootApplication
@EnableKafkaStreams
@EnableScheduling
public class DataPipelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataPipelineApplication.class, args);
    }
}
