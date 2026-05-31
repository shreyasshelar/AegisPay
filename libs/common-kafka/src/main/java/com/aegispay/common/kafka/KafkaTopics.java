package com.aegispay.common.kafka;

/**
 * Canonical topic names for the entire platform.
 * All producers and consumers reference these constants — never inline strings.
 */
public final class KafkaTopics {

    // Transaction lifecycle
    public static final String TRANSACTION_INITIATED     = "transaction.initiated";
    public static final String TRANSACTION_COMPLETED     = "transaction.completed";
    public static final String TRANSACTION_FAILED        = "transaction.failed";
    public static final String TRANSACTION_ROLLED_BACK   = "transaction.rolled-back";

    // Balance / Ledger
    public static final String BALANCE_RESERVE_REQUESTED = "balance.reserve.requested";
    public static final String BALANCE_RESERVED          = "balance.reserved";
    public static final String BALANCE_RESERVE_FAILED    = "balance.reserve.failed";
    public static final String BALANCE_COMMIT_REQUESTED  = "balance.commit.requested";
    public static final String BALANCE_COMMITTED         = "balance.committed";
    public static final String BALANCE_ROLLBACK_REQUESTED = "balance.rollback.requested";
    public static final String BALANCE_ROLLED_BACK       = "balance.rolled-back";

    // Risk
    public static final String RISK_ASSESSMENT_REQUESTED = "risk.assessment.requested";
    public static final String RISK_ASSESSED             = "risk.assessed";

    // Payment gateway
    public static final String PAYMENT_PROCESS_REQUESTED = "payment.process.requested";
    public static final String PAYMENT_PROCESSED         = "payment.processed";

    // User / KYC
    public static final String USER_REGISTERED           = "user.registered";
    public static final String USER_CONTACT_UPDATED      = "user.contact.updated";
    public static final String KYC_STATUS_CHANGED        = "kyc.status.changed";

    // Notifications
    public static final String NOTIFICATION_SEND_REQUESTED = "notification.send.requested";

    // ── Dead Letter Queue topic constants (compile-time for use in @KafkaListener) ──
    public static final String TRANSACTION_INITIATED_DLQ      = TRANSACTION_INITIATED      + ".DLQ";
    public static final String TRANSACTION_COMPLETED_DLQ      = TRANSACTION_COMPLETED      + ".DLQ";
    public static final String TRANSACTION_FAILED_DLQ         = TRANSACTION_FAILED         + ".DLQ";
    public static final String TRANSACTION_ROLLED_BACK_DLQ    = TRANSACTION_ROLLED_BACK    + ".DLQ";

    public static final String BALANCE_RESERVE_REQUESTED_DLQ  = BALANCE_RESERVE_REQUESTED  + ".DLQ";
    public static final String BALANCE_RESERVED_DLQ           = BALANCE_RESERVED           + ".DLQ";
    public static final String BALANCE_RESERVE_FAILED_DLQ     = BALANCE_RESERVE_FAILED     + ".DLQ";
    public static final String BALANCE_COMMIT_REQUESTED_DLQ   = BALANCE_COMMIT_REQUESTED   + ".DLQ";
    public static final String BALANCE_COMMITTED_DLQ          = BALANCE_COMMITTED          + ".DLQ";
    public static final String BALANCE_ROLLBACK_REQUESTED_DLQ = BALANCE_ROLLBACK_REQUESTED + ".DLQ";
    public static final String BALANCE_ROLLED_BACK_DLQ        = BALANCE_ROLLED_BACK        + ".DLQ";

    public static final String RISK_ASSESSMENT_REQUESTED_DLQ  = RISK_ASSESSMENT_REQUESTED  + ".DLQ";
    public static final String RISK_ASSESSED_DLQ              = RISK_ASSESSED              + ".DLQ";

    public static final String PAYMENT_PROCESS_REQUESTED_DLQ  = PAYMENT_PROCESS_REQUESTED  + ".DLQ";
    public static final String PAYMENT_PROCESSED_DLQ          = PAYMENT_PROCESSED          + ".DLQ";

    public static final String USER_REGISTERED_DLQ            = USER_REGISTERED            + ".DLQ";
    public static final String KYC_STATUS_CHANGED_DLQ         = KYC_STATUS_CHANGED         + ".DLQ";
    public static final String NOTIFICATION_SEND_REQUESTED_DLQ = NOTIFICATION_SEND_REQUESTED + ".DLQ";

    /** Non-annotation use: build DLQ topic name at runtime. */
    public static String dlq(String topic) {
        return topic + ".DLQ";
    }

    private KafkaTopics() {}
}
