package com.example.servicedeskapk.push

/**
 * Ключи payload FCM (data) и Intent для deep link.
 * Бэкенд должен передавать в `data`: ticket_id, action_type.
 */
object PushConstants {
    const val EXTRA_TICKET_ID = "ticket_id"
    const val EXTRA_ACTION_TYPE = "action_type"
    const val EXTRA_NOTIFICATION_TITLE = "notification_title"
    const val EXTRA_NOTIFICATION_BODY = "notification_body"

    const val NOTIFICATION_CHANNEL_ID = "servicedesk_push"
    const val NOTIFICATION_CHANNEL_NAME = "Заявки и уведомления"

    /** action_type из сервера (примеры) */
    const val ACTION_TICKET_ASSIGNED = "ticket_assigned"
    const val ACTION_STATUS_CHANGED = "status_changed"
    const val ACTION_COMMENT = "comment"
    const val ACTION_GENERIC = "notification"
}
