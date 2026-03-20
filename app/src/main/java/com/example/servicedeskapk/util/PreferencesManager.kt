package com.example.servicedeskapk.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Менеджер настроек приложения.
 * Хранит URL сервера, флаг авторизации, данные текущего пользователя.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("service_desk_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_FIRST_NAME = "first_name"
        private const val KEY_LAST_NAME = "last_name"
        private const val KEY_IS_ADMIN = "is_admin"
        private const val KEY_IS_SUPER_ADMIN = "is_super_admin"
        private const val KEY_MUST_CHANGE_PASSWORD = "must_change_password"
        private const val KEY_USE_STANDARD_AUTH_ROUTES = "use_standard_auth_routes"
        private const val KEY_PUSH_NOTIFICATION_PROMPT_SHOWN = "push_notification_prompt_shown"

        /** Нейтральный адрес по умолчанию (не указывает на реальный сервер). Пользователь задаёт свой в настройках. */
        const val DEFAULT_SERVER_URL = "https://example.invalid"
    }

    var useStandardAuthRoutes: Boolean
        get() = prefs.getBoolean(KEY_USE_STANDARD_AUTH_ROUTES, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_STANDARD_AUTH_ROUTES, value).apply()

    /** Системный диалог POST_NOTIFICATIONS уже показывали (не спамить при каждом заходе на главный). */
    var pushNotificationPromptShown: Boolean
        get() = prefs.getBoolean(KEY_PUSH_NOTIFICATION_PROMPT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_PUSH_NOTIFICATION_PROMPT_SHOWN, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var userId: Long
        get() = try {
            prefs.getLong(KEY_USER_ID, -1L)
        } catch (_: ClassCastException) {
            val intVal = prefs.getInt(KEY_USER_ID, -1).toLong()
            prefs.edit().remove(KEY_USER_ID).putLong(KEY_USER_ID, intVal).apply()
            intVal
        }
        set(value) = prefs.edit().putLong(KEY_USER_ID, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var firstName: String
        get() = prefs.getString(KEY_FIRST_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FIRST_NAME, value).apply()

    var lastName: String
        get() = prefs.getString(KEY_LAST_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_NAME, value).apply()

    var isAdmin: Boolean
        get() = prefs.getBoolean(KEY_IS_ADMIN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_ADMIN, value).apply()

    var isSuperAdmin: Boolean
        get() = prefs.getBoolean(KEY_IS_SUPER_ADMIN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_SUPER_ADMIN, value).apply()

    var mustChangePassword: Boolean
        get() = prefs.getBoolean(KEY_MUST_CHANGE_PASSWORD, false)
        set(value) = prefs.edit().putBoolean(KEY_MUST_CHANGE_PASSWORD, value).apply()

    val displayName: String
        get() {
            val name = listOfNotNull(
                firstName.takeIf { it.isNotEmpty() },
                lastName.takeIf { it.isNotEmpty() }
            ).joinToString(" ")
            return name.ifEmpty { username.ifEmpty { "Пользователь" } }
        }

    fun saveUser(
        id: Long,
        username: String?,
        firstName: String?,
        lastName: String?,
        isAdmin: Boolean,
        isSuperAdmin: Boolean,
        mustChangePassword: Boolean
    ) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putLong(KEY_USER_ID, id)
            .putString(KEY_USERNAME, username ?: "")
            .putString(KEY_FIRST_NAME, firstName ?: "")
            .putString(KEY_LAST_NAME, lastName ?: "")
            .putBoolean(KEY_IS_ADMIN, isAdmin)
            .putBoolean(KEY_IS_SUPER_ADMIN, isSuperAdmin)
            .putBoolean(KEY_MUST_CHANGE_PASSWORD, mustChangePassword)
            .apply()
    }

    fun clearUser() {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_FIRST_NAME)
            .remove(KEY_LAST_NAME)
            .remove(KEY_IS_ADMIN)
            .remove(KEY_IS_SUPER_ADMIN)
            .remove(KEY_MUST_CHANGE_PASSWORD)
            .apply()
    }
}
