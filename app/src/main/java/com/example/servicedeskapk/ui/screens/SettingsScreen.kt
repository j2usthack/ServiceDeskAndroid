package com.example.servicedeskapk.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.servicedeskapk.ServiceDeskApp
import com.example.servicedeskapk.data.api.RetrofitClient
import com.example.servicedeskapk.data.model.DeviceTokenUnregisterRequest
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import com.example.servicedeskapk.ui.components.ProfileMenuItem
import com.example.servicedeskapk.util.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as ServiceDeskApp).preferencesManager

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    data class State(
        val serverUrl: String = "",
        val useStandardAuthRoutes: Boolean = false,
        val isLoggedIn: Boolean = false,
        val displayName: String = "",
        val username: String = "",
        val isAdmin: Boolean = false,
        val isSuperAdmin: Boolean = false,
        val savedMessage: String? = null
    )

    init {
        _state.value = State(
            serverUrl = prefs.serverUrl,
            useStandardAuthRoutes = prefs.useStandardAuthRoutes,
            isLoggedIn = prefs.isLoggedIn,
            displayName = prefs.displayName,
            username = prefs.username,
            isAdmin = prefs.isAdmin,
            isSuperAdmin = prefs.isSuperAdmin
        )
    }

    fun setUseStandardAuthRoutes(use: Boolean) {
        prefs.useStandardAuthRoutes = use
        RetrofitClient.reset()
        _state.value = _state.value.copy(useStandardAuthRoutes = use)
    }

    fun onServerUrlChanged(url: String) {
        _state.value = _state.value.copy(serverUrl = url, savedMessage = null)
    }

    fun saveServerUrl() {
        val url = _state.value.serverUrl.trim()
        if (url.isBlank()) return
        prefs.serverUrl = url
        RetrofitClient.reset() // Сбрасываем клиент при смене URL
        _state.value = _state.value.copy(savedMessage = "Адрес сервера сохранён")
    }

    fun resetToDefault() {
        prefs.serverUrl = PreferencesManager.DEFAULT_SERVER_URL
        RetrofitClient.reset()
        _state.value = _state.value.copy(
            serverUrl = PreferencesManager.DEFAULT_SERVER_URL,
            savedMessage = "Адрес сброшен на стандартный"
        )
    }

    fun logout() {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                val token = try {
                    Tasks.await(FirebaseMessaging.getInstance().token)
                } catch (_: Exception) {
                    null
                }
                if (!token.isNullOrBlank()) {
                    try {
                        api.unregisterDeviceToken(DeviceTokenUnregisterRequest(token))
                    } catch (_: Exception) { }
                }
                api.logout()
            } catch (_: Exception) { }
            RetrofitClient.clearSession()
            prefs.clearUser()
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(savedMessage = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Профиль пользователя (если залогинен)
            if (state.isLoggedIn) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    state.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (state.username.isNotBlank()) {
                                    Text(
                                        "@${state.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val role = when {
                                    state.isSuperAdmin -> "Супер-администратор"
                                    state.isAdmin -> "Администратор"
                                    else -> "Пользователь"
                                }
                                Text(
                                    role,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Секция: Подключение
            Text(
                "Подключение к серверу",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = viewModel::onServerUrlChanged,
                label = { Text("Адрес сервера") },
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                placeholder = { Text("http://192.168.1.100:3000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Пример: https://your-server:8000")
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Стандартный маршрут входа (/login)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Включить, если на сервере доступен только /login, а не /api/auth/login",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.useStandardAuthRoutes,
                    onCheckedChange = viewModel::setUseStandardAuthRoutes
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = viewModel::saveServerUrl,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Сохранить")
                }
                OutlinedButton(
                    onClick = viewModel::resetToDefault,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("По умолчанию")
                }
            }

            // Выход
            if (state.isLoggedIn) {
                Spacer(modifier = Modifier.height(32.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.logout()
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выйти из аккаунта")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Обновление
            Text(
                "Обновление",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            val ctx = LocalContext.current
            ProfileMenuItem(
                icon = Icons.Default.Download,
                title = "Скачать обновление",
                subtitle = "Загрузить актуальную версию APK с GitHub",
                onClick = {
                    val url = "https://github.com/j2usthack/ServiceDeskAndroid/raw/main/releases/app-debug.apk"
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Информация о приложении
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.SupportAgent,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Управление заявками",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "IT-отдел университета",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Версия 1.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
