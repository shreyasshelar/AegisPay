package com.aegispay.common.domain.enums;

public enum NotificationType {
    TRANSACTION_COMPLETED,
    TRANSACTION_FAILED,
    TRANSACTION_ROLLED_BACK,
    /** Sent to the *payee* when a transaction completes — "You received X INR". */
    MONEY_RECEIVED,
    KYC_STATUS_CHANGED,
    USER_REGISTERED,
    GENERIC
}
