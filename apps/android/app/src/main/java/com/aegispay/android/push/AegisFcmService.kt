package com.aegispay.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aegispay.android.auth.TokenStore
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.ui.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CHANNEL_ID   = "aegispay_notifications"
private const val CHANNEL_NAME = "AegisPay Alerts"

@AndroidEntryPoint
class AegisFcmService : FirebaseMessagingService() {

    @Inject lateinit var api:                    AegisApiService
    @Inject lateinit var tokenStore:             TokenStore
    @Inject lateinit var notificationBadgeState: NotificationBadgeState

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Token refresh ─────────────────────────────────────────────────────────

    override fun onNewToken(fcmToken: String) {
        super.onNewToken(fcmToken)
        val userId = tokenStore.userId ?: return
        serviceScope.launch {
            runCatching {
                api.registerPushToken(
                    userId = userId,
                    body   = mapOf("token" to fcmToken, "platform" to "android"),
                )
            }
        }
    }

    // ── Incoming message ──────────────────────────────────────────────────────

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "AegisPay"
        val body  = message.notification?.body
            ?: message.data["body"]
            ?: ""

        notificationBadgeState.increment()
        showNotification(title, body)
    }

    // ── Local notification display ────────────────────────────────────────────

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (no-op on subsequent calls)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Transaction and account updates" }
        manager.createNotificationChannel(channel)

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)   // replace with branded icon
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
