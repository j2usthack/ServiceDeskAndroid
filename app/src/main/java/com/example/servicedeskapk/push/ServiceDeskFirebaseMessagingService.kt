package com.example.servicedeskapk.push

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.servicedeskapk.ServiceDeskApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Обработка FCM:
 * - **Foreground:** только in-app событие (Snackbar), без дублирования в шторку.
 * - **Background / killed:** системное уведомление [showTicketPushNotification].
 *
 * Ожидаемые поля `data`: `ticket_id`, `action_type` (опционально).
 * Поля `notification.title/body` используются для текста, если нет — из `data`.
 */
class ServiceDeskFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        FcmRegistrationHelper.onTokenRefresh(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val ticketId = data["ticket_id"]?.toIntOrNull()
        val actionType = data["action_type"] ?: PushConstants.ACTION_GENERIC

        val title = message.notification?.title
            ?: data["title"]
            ?: applicationContext.getString(com.example.servicedeskapk.R.string.app_name)
        val body = message.notification?.body
            ?: data["body"]
            ?: data["message"]
            ?: ""

        val app = application as? ServiceDeskApp ?: return
        val isForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

        if (isForeground) {
            app.foregroundPushEvents.tryEmit(
                ForegroundPushPayload(
                    title = title,
                    body = body,
                    ticketId = ticketId,
                    actionType = actionType
                )
            )
        } else {
            showTicketPushNotification(
                context = applicationContext,
                title = title,
                body = body,
                ticketId = ticketId,
                actionType = actionType
            )
        }
    }

    companion object {
        private const val TAG = "ServiceDeskFCM"
    }
}
