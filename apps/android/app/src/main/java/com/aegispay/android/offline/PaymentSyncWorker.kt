package com.aegispay.android.offline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.CreateTransactionRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that replays pending offline payments once the device
 * reconnects to the network.
 *
 * Behaviour:
 *  1. On start, reset any stuck SYNCING rows from a previous crash.
 *  2. Fetch all PENDING rows in FIFO order.
 *  3. For each row: mark SYNCING → call API with original idempotency key →
 *       • 202/200 → mark DONE
 *       • 4xx (non-409) → mark FAILED (unretriable)
 *       • 409 Conflict → treated as DONE (server already processed it)
 *       • 5xx / network error → leave PENDING, worker returns RETRY
 *  4. Prune terminal rows older than [PRUNE_AGE_DAYS].
 */
@HiltWorker
class PaymentSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: OfflinePaymentDao,
    private val api: AegisApiService,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME     = "offline_payment_sync"
        private const val PRUNE_AGE_DAYS = 7L
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Recover any rows stuck in SYNCING from a previous worker crash
        dao.resetStuckSyncing()

        val pending = dao.pendingPayments()
        if (pending.isEmpty()) {
            pruneOld()
            return@withContext Result.success()
        }

        var encounteredServerError = false

        for (payment in pending) {
            dao.markSyncing(payment.idempotencyKey)

            try {
                val txn = api.createTransaction(
                    idempotencyKey = payment.idempotencyKey,
                    request = CreateTransactionRequest(
                        payeeId  = payment.payeeId,
                        amount   = payment.amount,
                        currency = payment.currency,
                        note     = payment.note,
                    ),
                )
                dao.markDone(payment.idempotencyKey, txn.transactionId)

            } catch (e: HttpException) {
                when {
                    e.code() == 409 -> {
                        // Conflict = already processed — treat as success
                        dao.markDone(payment.idempotencyKey, "idempotent-replay")
                    }
                    e.code() in 400..499 -> {
                        // Client error — don't retry
                        dao.markFailed(payment.idempotencyKey, "HTTP ${e.code()}: ${e.message()}")
                    }
                    else -> {
                        // 5xx — reset to PENDING and signal RETRY
                        dao.update(payment.copy(status = OfflinePaymentStatus.PENDING))
                        encounteredServerError = true
                    }
                }
            } catch (e: Exception) {
                // Network error — reset and retry later
                dao.update(payment.copy(status = OfflinePaymentStatus.PENDING))
                encounteredServerError = true
            }
        }

        pruneOld()

        if (encounteredServerError) Result.retry() else Result.success()
    }

    private suspend fun pruneOld() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(PRUNE_AGE_DAYS)
        dao.pruneOldTerminal(cutoff)
    }
}
