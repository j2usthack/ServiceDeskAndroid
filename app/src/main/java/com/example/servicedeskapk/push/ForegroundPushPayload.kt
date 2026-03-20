package com.example.servicedeskapk.push

/**
 * Событие push, когда приложение на переднем плане — показываем Snackbar, не дублируем шторку.
 */
data class ForegroundPushPayload(
    val title: String?,
    val body: String?,
    val ticketId: Int?,
    val actionType: String?
)
