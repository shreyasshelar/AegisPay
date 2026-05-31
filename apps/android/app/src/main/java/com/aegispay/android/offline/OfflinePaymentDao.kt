package com.aegispay.android.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflinePaymentDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(payment: OfflinePaymentEntity)

    /** Returns all PENDING payments ordered oldest-first for FIFO replay. */
    @Query("""
        SELECT * FROM offline_payments
        WHERE status = 'PENDING'
        ORDER BY created_at ASC
    """)
    suspend fun pendingPayments(): List<OfflinePaymentEntity>

    /** Live count for the "offline queue" badge in the UI. */
    @Query("SELECT COUNT(*) FROM offline_payments WHERE status = 'PENDING'")
    fun pendingCount(): Flow<Int>

    @Update
    suspend fun update(payment: OfflinePaymentEntity)

    @Query("""
        UPDATE offline_payments
        SET status = 'SYNCING', retry_count = retry_count + 1
        WHERE idempotency_key = :key
    """)
    suspend fun markSyncing(key: String)

    @Query("""
        UPDATE offline_payments
        SET status = 'DONE', server_transaction_id = :txnId
        WHERE idempotency_key = :key
    """)
    suspend fun markDone(key: String, txnId: String)

    @Query("""
        UPDATE offline_payments
        SET status = 'FAILED', failure_reason = :reason
        WHERE idempotency_key = :key
    """)
    suspend fun markFailed(key: String, reason: String)

    /** Reset SYNCING rows to PENDING if the worker crashed mid-sync. */
    @Query("UPDATE offline_payments SET status = 'PENDING' WHERE status = 'SYNCING'")
    suspend fun resetStuckSyncing()

    /** Remove terminal rows older than [cutoffMs] to avoid indefinite growth. */
    @Query("""
        DELETE FROM offline_payments
        WHERE status IN ('DONE', 'FAILED')
          AND created_at < :cutoffMs
    """)
    suspend fun pruneOldTerminal(cutoffMs: Long)
}
