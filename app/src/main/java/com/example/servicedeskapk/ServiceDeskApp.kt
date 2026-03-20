package com.example.servicedeskapk

import android.app.Application
import com.example.servicedeskapk.push.ForegroundPushPayload
import com.example.servicedeskapk.push.ensurePushNotificationChannel
import com.example.servicedeskapk.util.PreferencesManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class ServiceDeskApp : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set

    /**
     * Push при открытом приложении — подписывается MainActivity (Snackbar),
     * чтобы не дублировать системную шторку.
     */
    val foregroundPushEvents = MutableSharedFlow<ForegroundPushPayload>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Запрос навигации на экран заявки (из уведомления / cold start / singleTop).
     */
    val navigateToTicketRequests = MutableSharedFlow<Int>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Если пользователь не залогинен, а по push пришёл ticket_id — откроем после входа.
     */
    @Volatile
    var deferredDeepLinkTicketId: Int? = null

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        ensurePushNotificationChannel(this)
    }
}
