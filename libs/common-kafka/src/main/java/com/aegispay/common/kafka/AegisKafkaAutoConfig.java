package com.aegispay.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Auto-configuration shared by every AegisPay service.
 *
 * NewTopic beans are idempotent: KafkaAdmin creates the topic on first startup
 * and skips it on subsequent startups — no UNKNOWN_TOPIC_OR_PARTITION warnings
 * at consumer boot time, no manual topic creation steps needed in any environment.
 *
 * Partitions=3, replicas=1 for local dev (single-broker).
 * Override per-environment via spring.kafka.admin.properties if needed.
 */
@AutoConfiguration
@EnableKafka
@Import({KafkaConsumerRetryConfig.class})
public class AegisKafkaAutoConfig {

    private static final int PARTITIONS = 3;
    private static final int REPLICAS   = 1;

    // ── Topic declarations ────────────────────────────────────────────────────

    @Bean public NewTopic topicTransactionInitiated()     { return topic(KafkaTopics.TRANSACTION_INITIATED); }
    @Bean public NewTopic topicTransactionCompleted()     { return topic(KafkaTopics.TRANSACTION_COMPLETED); }
    @Bean public NewTopic topicTransactionFailed()        { return topic(KafkaTopics.TRANSACTION_FAILED); }
    @Bean public NewTopic topicTransactionRolledBack()    { return topic(KafkaTopics.TRANSACTION_ROLLED_BACK); }

    @Bean public NewTopic topicBalanceReserveRequested()  { return topic(KafkaTopics.BALANCE_RESERVE_REQUESTED); }
    @Bean public NewTopic topicBalanceReserved()          { return topic(KafkaTopics.BALANCE_RESERVED); }
    @Bean public NewTopic topicBalanceReserveFailed()     { return topic(KafkaTopics.BALANCE_RESERVE_FAILED); }
    @Bean public NewTopic topicBalanceCommitRequested()   { return topic(KafkaTopics.BALANCE_COMMIT_REQUESTED); }
    @Bean public NewTopic topicBalanceCommitted()         { return topic(KafkaTopics.BALANCE_COMMITTED); }
    @Bean public NewTopic topicBalanceRollbackRequested() { return topic(KafkaTopics.BALANCE_ROLLBACK_REQUESTED); }
    @Bean public NewTopic topicBalanceRolledBack()        { return topic(KafkaTopics.BALANCE_ROLLED_BACK); }

    @Bean public NewTopic topicRiskAssessmentRequested()  { return topic(KafkaTopics.RISK_ASSESSMENT_REQUESTED); }
    @Bean public NewTopic topicRiskAssessed()             { return topic(KafkaTopics.RISK_ASSESSED); }

    @Bean public NewTopic topicPaymentProcessRequested()  { return topic(KafkaTopics.PAYMENT_PROCESS_REQUESTED); }
    @Bean public NewTopic topicPaymentProcessed()         { return topic(KafkaTopics.PAYMENT_PROCESSED); }

    @Bean public NewTopic topicUserRegistered()           { return topic(KafkaTopics.USER_REGISTERED); }
    @Bean public NewTopic topicKycStatusChanged()         { return topic(KafkaTopics.KYC_STATUS_CHANGED); }

    @Bean public NewTopic topicNotificationSendRequested(){ return topic(KafkaTopics.NOTIFICATION_SEND_REQUESTED); }

    // DLQ topics for every business topic
    @Bean public NewTopic topicTransactionInitiatedDlq()     { return topic(KafkaTopics.dlq(KafkaTopics.TRANSACTION_INITIATED)); }
    @Bean public NewTopic topicTransactionCompletedDlq()     { return topic(KafkaTopics.dlq(KafkaTopics.TRANSACTION_COMPLETED)); }
    @Bean public NewTopic topicTransactionFailedDlq()        { return topic(KafkaTopics.dlq(KafkaTopics.TRANSACTION_FAILED)); }
    @Bean public NewTopic topicTransactionRolledBackDlq()    { return topic(KafkaTopics.dlq(KafkaTopics.TRANSACTION_ROLLED_BACK)); }
    @Bean public NewTopic topicBalanceReserveRequestedDlq()  { return topic(KafkaTopics.dlq(KafkaTopics.BALANCE_RESERVE_REQUESTED)); }
    @Bean public NewTopic topicBalanceReservedDlq()          { return topic(KafkaTopics.dlq(KafkaTopics.BALANCE_RESERVED)); }
    @Bean public NewTopic topicBalanceReserveFailedDlq()     { return topic(KafkaTopics.dlq(KafkaTopics.BALANCE_RESERVE_FAILED)); }
    @Bean public NewTopic topicBalanceCommitRequestedDlq()   { return topic(KafkaTopics.dlq(KafkaTopics.BALANCE_COMMIT_REQUESTED)); }
    @Bean public NewTopic topicBalanceCommittedDlq()         { return topic(KafkaTopics.dlq(KafkaTopics.BALANCE_COMMITTED)); }
    @Bean public NewTopic topicBalanceRollbackRequestedDlq() { return topic(KafkaTopics.dlq(KafkaTopics.BALANCE_ROLLBACK_REQUESTED)); }
    @Bean public NewTopic topicBalanceRolledBackDlq()        { return topic(KafkaTopics.dlq(KafkaTopics.BALANCE_ROLLED_BACK)); }
    @Bean public NewTopic topicRiskAssessmentRequestedDlq()  { return topic(KafkaTopics.dlq(KafkaTopics.RISK_ASSESSMENT_REQUESTED)); }
    @Bean public NewTopic topicRiskAssessedDlq()             { return topic(KafkaTopics.dlq(KafkaTopics.RISK_ASSESSED)); }
    @Bean public NewTopic topicPaymentProcessRequestedDlq()  { return topic(KafkaTopics.dlq(KafkaTopics.PAYMENT_PROCESS_REQUESTED)); }
    @Bean public NewTopic topicPaymentProcessedDlq()         { return topic(KafkaTopics.dlq(KafkaTopics.PAYMENT_PROCESSED)); }
    @Bean public NewTopic topicUserRegisteredDlq()           { return topic(KafkaTopics.dlq(KafkaTopics.USER_REGISTERED)); }
    @Bean public NewTopic topicKycStatusChangedDlq()         { return topic(KafkaTopics.dlq(KafkaTopics.KYC_STATUS_CHANGED)); }
    @Bean public NewTopic topicNotificationSendRequestedDlq(){ return topic(KafkaTopics.dlq(KafkaTopics.NOTIFICATION_SEND_REQUESTED)); }

    // ── Shared infrastructure beans ───────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public DlqPublisher dlqPublisher(
            org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate) {
        return new DlqPublisher(kafkaTemplate);
    }

    @Bean
    public AegisKafkaProducerTemplate aegisKafkaProducerTemplate(
            org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        return new AegisKafkaProducerTemplate(kafkaTemplate, objectMapper);
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }
}
