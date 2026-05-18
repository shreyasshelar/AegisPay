package com.aegispay.android.offline

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * Represents a payment request that was captured while the device was offline.
 *
 * The [idempotencyKey] is generated at creation time and survives retries, ensuring
 * the server de-duplicates the request even if the worker fires multiple times.
 *
 * Lifecycle:
 *   PENDING  → worker picks it up
 *   SYNCING  → worker is actively submitting
 *   DONE     → server accepted (or previously processed via idempotency)
 *   FAILED   → server returned an unretriable error (e.g. 400 Bad Request)
 */
@Entity(tableName = "offline_payments")
data class OfflinePaymentEntity(

    @PrimaryKey
    val idempotencyKey: String,

    @ColumnInfo(name = "payee_id")
    val payeeId: String,

    val amount: BigDecimal,
    val currency: String,
    val note: String?,

    /** Epoch millis — used for ordering and TTL expiry (7 days). */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    val status: OfflinePaymentStatus = OfflinePaymentStatus.PENDING,

    /** Set to the server's transactionId once synced successfully. */
    @ColumnInfo(name = "server_transaction_id")
    val serverTransactionId: String? = null,

    /** Human-readable failure reason if status == FAILED. */
    @ColumnInfo(name = "failure_reason")
    val failureReason: String? = null,
)

enum class OfflinePaymentStatus { PENDING, SYNCING, DONE, FAILED }
