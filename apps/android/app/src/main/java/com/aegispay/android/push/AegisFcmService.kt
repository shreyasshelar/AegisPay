package com.aegispay.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
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

// ── Channel constants ─────────────────────────────────────────────────────────

/** Successful transaction channel — default importance, standard notification sound. */
private const val COMPLETED_CHANNEL_ID   = "aegispay_completed"
private const val COMPLETED_CHANNEL_NAME = "Completed Transfers"

/**
 * Failed/flagged transaction channel — high importance with distinct urgent sound.
 * Overrides DND so the user is immediately aware of a problem.
 */
private const val FAILED_CHANNEL_ID   = "aegispay_failed"
private const val FAILED_CHANNEL_NAME = "Failed / Flagged Transfers"

/** General announcements (KYC updates, promotions). */
private const val GENERAL_CHANNEL_ID   = "aegispay_general"
private const val GENERAL_CHANNEL_NAME = "AegisPay Updates"

// ── Notification type detection ───────────────────────────────────────────────

private enum class NotificationType { COMPLETED, FAILED, GENERAL }

private fun RemoteMessage.resolveType(): NotificationType {
    // Backend stamps a "type" data field to drive channel routing
    val type = data["type"]?.uppercase() ?: ""
    return when {
        type == "TRANSACTION_COMPLETED" ||
        type == "PAYMENT_SUCCESS"       -> NotificationType.COMPLETED

        type == "TRANSACTION_FAILED"    ||
        type == "PAYMENT_FAILED"        ||
        type == "FRAUD_ALERT"           ||
        type == "EFW_ALERT"             -> NotificationType.FAILED

        else -> {
            // Fallback: sniff the notification title / body
            val title = notification?.title?.uppercase() ?: ""
            val body  = notification?.body?.uppercase()  ?: ""
            when {
                title.contains("SUCCESS") || title.contains("COMPLETED") ||
                body.contains("SUCCESS")  || body.contains("COMPLETED")  -> NotificationType.COMPLETED

                title.contains("FAIL") || title.contains("ALERT")  ||
                body.contains("FAIL")  || body.contains("ALERT")   -> NotificationType.FAILED

                else -> NotificationType.GENERAL
            }
        }
    }
}

// ── Service ───────────────────────────────────────────────────────────────────

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

        val type = message.resolveType()
        ensureChannelsCreated()
        showNotification(title, body, type)
    }

    // ── Channel initialisation ────────────────────────────────────────────────

    /**
     * Creates all notification channels idempotently.
     * On API 26+ channels persist even after the app is uninstalled; calling
     * `createNotificationChannel` repeatedly is a no-op once a channel exists.
     */
    private fun ensureChannelsCreated() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // COMPLETED — default importance, standard sound
        val completedChannel = NotificationChannel(
            COMPLETED_CHANNEL_ID,
            COMPLETED_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifications for successfully completed transfers"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 150, 100, 150)
        }
        manager.createNotificationChannel(completedChannel)

        // FAILED — high importance + distinct urgent alarm-tone
        val failedChannel = NotificationChannel(
            FAILED_CHANNEL_ID,
            FAILED_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Critical alerts for failed, reversed, or fraud-flagged transactions"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 100, 300, 100, 300)
            // Use the built-in alarm sound to clearly differentiate from normal alerts
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, attrs)
        }
        manager.createNotificationChannel(failedChannel)

        // GENERAL — low importance for informational messages
        val generalChannel = NotificationChannel(
            GENERAL_CHANNEL_ID,
            GENERAL_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "KYC updates, promotions and general AegisPay announcements"
        }
        manager.createNotificationChannel(generalChannel)
    }

    // ── Local notification display ────────────────────────────────────────────

    private fun showNotification(title: String, body: String, type: NotificationType) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = when (type) {
            NotificationType.COMPLETED -> COMPLETED_CHANNEL_ID
            NotificationType.FAILED    -> FAILED_CHANNEL_ID
            NotificationType.GENERAL   -> GENERAL_CHANNEL_ID
        }

        val priority = when (type) {
            NotificationType.FAILED -> NotificationCompat.PRIORITY_HIGH
            else                    -> NotificationCompat.PRIORITY_DEFAULT
        }

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Deep-link extras so MainActivity can navigate directly to the right screen
            putExtra("notification_type", type.name)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, type.ordinal, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)   // replace with branded icon
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
