package com.verystrongdog.packagesyncprobe

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object ProbeNotificationHelper {
    const val ACTION_REVIEW_NOTIFICATION = "com.verystrongdog.packagesyncprobe.action.REVIEW_NOTIFICATION"
    const val EXTRA_NOTIFICATION_REASON = "notification_reason"
    const val FOREGROUND_NOTIFICATION_ID = 1001
    private const val REVIEW_NOTIFICATION_ID = 1002
    private const val FOREGROUND_CHANNEL_ID = "probe_foreground_channel"
    private const val REVIEW_CHANNEL_ID = "probe_review_channel"

    fun ensureChannels(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        val foregroundChannel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            context.getString(R.string.foreground_service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.foreground_service_channel_description)
        }
        val reviewChannel = NotificationChannel(
            REVIEW_CHANNEL_ID,
            context.getString(R.string.review_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.review_notification_channel_description)
        }
        notificationManager.createNotificationChannel(foregroundChannel)
        notificationManager.createNotificationChannel(reviewChannel)
    }

    @SuppressLint("MissingPermission")
    fun postReviewNotification(context: Context, reason: String): String {
        check(canPostNotifications(context)) {
            context.getString(R.string.notification_permission_required)
        }
        ensureChannels(context)
        val reviewIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_REVIEW_NOTIFICATION
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_REASON, reason)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REVIEW_NOTIFICATION_ID,
            reviewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, REVIEW_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.review_notification_title))
            .setContentText(context.getString(R.string.review_notification_text, reason))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(REVIEW_NOTIFICATION_ID, notification)
        return context.getString(R.string.notification_posted_summary, reason)
    }

    fun buildForegroundNotification(context: Context, detail: String): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.foreground_service_notification_title))
            .setContentText(detail)
            .setOngoing(true)
            .build()
    }

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
