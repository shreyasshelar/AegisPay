package com.aegispay.android.offline

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade over the offline payment Room table + WorkManager scheduler.
 *
 * Callers in the UI layer (SendMoneyViewModel) use [enqueue] when the device
 * has no network instead of calling the API directly.  Once connectivity
 * returns, [PaymentSyncWorker] picks up PENDING rows and replays them.
 */
@Singleton
class OfflinePaymentQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: OfflinePaymentDao,
) {

    /** Number of pending (un-synced) payments — drives the UI badge. */
    val pendingCount: Flow<Int> = dao.pendingCount()

    /**
     * Persist a payment to the offline queue.
     *
     * A fresh UUID is generated as the idempotency key so it survives retries
     * and the device clock can't cause collisions.
     */
    suspend fun enqueue(
        payeeId:  String,
        amount:   BigDecimal,
        currency: String,
        note:     String?,
    ): String {
        val key = UUID.randomUUID().toString()
        dao.enqueue(
            OfflinePaymentEntity(
                idempotencyKey = key,
                payeeId        = payeeId,
                amount         = amount,
                currency       = currency,
                note           = note,
            )
        )
        scheduleSync()
        return key
    }

    /**
     * Schedule a [PaymentSyncWorker] if not already running.
     * Uses KEEP policy so multiple enqueue() calls don't stack duplicate workers.
     */
    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<PaymentSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                PaymentSyncWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
    }
}
