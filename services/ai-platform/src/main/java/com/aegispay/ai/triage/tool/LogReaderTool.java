package com.aegispay.ai.triage.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
public class LogReaderTool {

    @Tool(description = "Read recent error logs for a service. Returns the last N error/warn log lines for the given service name and time window in minutes.")
    public String readLogs(String serviceName, int windowMinutes) {
        log.info("LogReaderTool: reading logs for service={} window={}m", serviceName, windowMinutes);
        Instant since = Instant.now().minus(windowMinutes, ChronoUnit.MINUTES);

        // In production this would query Loki / CloudWatch / ELK.
        // Here we return a realistic stub so the agent can reason over it.
        return """
                [%s] Simulated log snapshot for service '%s' (last %d minutes):
                WARN  TransactionService    - Slow DB query detected: 2340ms on SELECT * FROM transactions
                ERROR PaymentGatewayClient  - Connection timeout to gateway.payments.example.com:443 (attempt 3/3)
                ERROR SagaOrchestrator      - SAGA_TIMEOUT on transactionId=8f3a2b1c, step=PROCESS_PAYMENT
                WARN  KafkaConsumer         - Consumer lag on topic 'payment.process.requested' partition=2 lag=1423
                ERROR LedgerService         - OptimisticLockingFailureException on accountId=d7e4f9a1 (retry 2/3)
                INFO  HealthIndicator       - DB pool usage: 94%% (47/50 connections in use)
                """.formatted(since, serviceName, windowMinutes);
    }
}
