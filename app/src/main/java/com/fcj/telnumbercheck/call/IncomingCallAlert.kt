package com.fcj.telnumbercheck.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fcj.telnumbercheck.R
import com.fcj.telnumbercheck.data.PhoneNumberNormalizer
import com.fcj.telnumbercheck.domain.CallerInfo
import com.fcj.telnumbercheck.ui.CallerPopupActivity

class IncomingCallAlert(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "来电检测提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "陌生来电检测卡片与弹窗提醒"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(rawNumber: String): Boolean {
        return post(
            rawNumber = rawNumber,
            title = "正在检测来电",
            text = PhoneNumberNormalizer.display(PhoneNumberNormalizer.normalize(rawNumber)),
            useFullScreenIntent = true,
        )
    }

    fun showResult(rawNumber: String, info: CallerInfo): Boolean =
        post(
            rawNumber = rawNumber,
            title = info.headline,
            text = info.detail,
            useFullScreenIntent = true,
        )

    fun showFailure(rawNumber: String, message: String): Boolean =
        post(
            rawNumber = rawNumber,
            title = "来电检测未完成",
            text = message,
            useFullScreenIntent = true,
        )

    private fun post(
        rawNumber: String,
        title: String,
        text: String,
        useFullScreenIntent: Boolean,
    ): Boolean {
        if (!CallAlertPermissions.canPostNotifications(context)) return false
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        createChannel()
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        val popupIntent = CallerPopupActivity.intent(context, normalized, fromIncomingCall = true)
        val popupPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            popupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${PhoneNumberNormalizer.display(normalized)}\n$text",
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(popupPendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(ALERT_TIMEOUT_MILLIS)
            .apply {
                if (useFullScreenIntent) setFullScreenIntent(popupPendingIntent, true)
            }
            .build()
        return runCatching {
            notificationManager.notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val CHANNEL_ID = "incoming_call_detection_v1"
        private const val NOTIFICATION_ID = 2401
        private const val ALERT_TIMEOUT_MILLIS = 30_000L
    }
}
