package com.example.servicedeskapk.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.servicedeskapk.MainActivity

/**
 * Показ системного уведомления (фон / закрытое приложение).
 */
fun showTicketPushNotification(
    context: Context,
    title: String,
    body: String,
    ticketId: Int?,
    actionType: String?
) {
    ensurePushNotificationChannel(context)
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        ticketId?.let { putExtra(PushConstants.EXTRA_TICKET_ID, it) }
        actionType?.let { putExtra(PushConstants.EXTRA_ACTION_TYPE, it) }
        putExtra(PushConstants.EXTRA_NOTIFICATION_TITLE, title)
        putExtra(PushConstants.EXTRA_NOTIFICATION_BODY, body)
    }
    val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    val pendingIntent = PendingIntent.getActivity(
        context,
        (ticketId ?: 0) and 0xFFFF,
        intent,
        pendingFlags
    )
    val notification = NotificationCompat.Builder(context, PushConstants.NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(com.example.servicedeskapk.R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(android.app.Notification.DEFAULT_SOUND or android.app.Notification.DEFAULT_VIBRATE)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
    NotificationManagerCompat.from(context).notify((ticketId ?: System.currentTimeMillis().toInt()) and 0x7FFFFFFF, notification)
}
