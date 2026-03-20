package com.example.servicedeskapk.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.servicedeskapk.ServiceDeskApp
import com.example.servicedeskapk.data.api.RetrofitClient
import com.example.servicedeskapk.data.model.DeviceTokenRequest
import com.example.servicedeskapk.data.model.DeviceTokenUnregisterRequest
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Регистрация FCM device token на бэкенде и снятие при выходе.
 *
 * API: `POST /api/user/device-token`, `DELETE /api/user/device-token`
 */
object FcmRegistrationHelper {

    private const val TAG = "FcmRegistration"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * После успешного входа: получить токен и отправить на сервер (если есть разрешение).
     */
    fun registerTokenAfterLogin(context: Context) {
        if (!hasNotificationPermission(context)) {
            Log.d(TAG, "Skip FCM register: no POST_NOTIFICATIONS permission")
            return
        }
        scope.launch {
            try {
                val token = Tasks.await(FirebaseMessaging.getInstance().token)
                if (token.isNullOrBlank()) return@launch
                sendTokenToBackend(context.applicationContext, token)
            } catch (e: Exception) {
                Log.w(TAG, "FCM token fetch failed", e)
            }
        }
    }

    fun onTokenRefresh(context: Context, newToken: String) {
        val app = context.applicationContext as? ServiceDeskApp ?: return
        if (!app.preferencesManager.isLoggedIn) return
        if (!hasNotificationPermission(context)) return
        scope.launch {
            sendTokenToBackend(context.applicationContext, newToken)
        }
    }

    private suspend fun sendTokenToBackend(context: Context, token: String) {
        val app = context.applicationContext as? ServiceDeskApp ?: return
        if (!app.preferencesManager.isLoggedIn) return
        val baseUrl = app.preferencesManager.serverUrl.trim()
        if (baseUrl.isBlank() || baseUrl.contains("example.invalid")) return
        withContext(Dispatchers.IO) {
            try {
                val api = RetrofitClient.getApiService(context, baseUrl)
                val resp = api.registerDeviceToken(DeviceTokenRequest(token = token, platform = "android"))
                if (resp.isSuccessful) {
                    Log.d(TAG, "Device token registered on server")
                } else {
                    Log.w(TAG, "Device token register failed: ${resp.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Device token register error", e)
            }
        }
    }

    /**
     * Вызов перед очисткой сессии (logout).
     */
    fun unregisterTokenOnLogout(context: Context) {
        val app = context.applicationContext as? ServiceDeskApp ?: return
        val baseUrl = app.preferencesManager.serverUrl.trim()
        if (baseUrl.isBlank() || baseUrl.contains("example.invalid")) return
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val api = RetrofitClient.getApiService(context.applicationContext, baseUrl)
                    val token = try {
                        Tasks.await(FirebaseMessaging.getInstance().token)
                    } catch (_: Exception) {
                        null
                    }
                    if (!token.isNullOrBlank()) {
                        api.unregisterDeviceToken(DeviceTokenUnregisterRequest(token))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Device token unregister error", e)
                }
            }
        }
    }
}
