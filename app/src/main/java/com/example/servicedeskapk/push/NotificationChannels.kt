package com.example.servicedeskapk.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Канал уведомлений Android 8+ для FCM.
 */
fun ensurePushNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        PushConstants.NOTIFICATION_CHANNEL_ID,
        PushConstants.NOTIFICATION_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Назначения заявок, комментарии, смена статуса"
        enableVibration(true)
        setShowBadge(true)
    }
    manager.createNotificationChannel(channel)
}
