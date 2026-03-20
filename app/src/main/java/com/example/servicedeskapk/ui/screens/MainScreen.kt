package com.example.servicedeskapk.ui.screens

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.servicedeskapk.ServiceDeskApp
import com.example.servicedeskapk.data.api.RetrofitClient
import com.example.servicedeskapk.data.model.DeviceTokenUnregisterRequest
import com.example.servicedeskapk.push.FcmRegistrationHelper
import com.example.servicedeskapk.ui.components.ProfileMenuItem
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ===================== ViewModel =====================

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as ServiceDeskApp).preferencesManager

    private val _notificationCount = MutableStateFlow(0)
    val notificationCount: StateFlow<Int> = _notificationCount

    val isAdmin get() = prefs.isAdmin
    val isSuperAdmin get() = prefs.isSuperAdmin
    val displayName get() = prefs.displayName
    val username get() = prefs.username

    init {
        // Периодическое обновление счётчика уведомлений
        viewModelScope.launch {
            while (true) {
                loadNotificationCount()
                delay(30_000) // каждые 30 секунд
            }
        }
    }

    fun loadNotificationCount() {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                val response = api.getNotificationCount()
                if (response.isSuccessful) {
                    _notificationCount.value = response.body()?.count ?: 0
                }
            } catch (_: Exception) { }
        }
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
}

// ===================== Main Screen =====================

@Composable
fun MainScreen(
    onNavigateToTicket: (Int) -> Unit,
    onNavigateToCreateTicket: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onLogout: () -> Unit,
    mainViewModel: MainViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val notificationCount by mainViewModel.notificationCount.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as ServiceDeskApp
    val prefsMgr = app.preferencesManager

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        prefsMgr.pushNotificationPromptShown = true
        if (granted) {
            FcmRegistrationHelper.registerTokenAfterLogin(context.applicationContext)
        }
    }

    LaunchedEffect(Unit) {
        val def = app.deferredDeepLinkTicketId ?: return@LaunchedEffect
        app.deferredDeepLinkTicketId = null
        delay(500)
        onNavigateToTicket(def)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (FcmRegistrationHelper.hasNotificationPermission(context)) {
                prefsMgr.pushNotificationPromptShown = true
                FcmRegistrationHelper.registerTokenAfterLogin(context.applicationContext)
            } else if (!prefsMgr.pushNotificationPromptShown) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            FcmRegistrationHelper.registerTokenAfterLogin(context.applicationContext)
        }
    }

    // Обновляем счётчик уведомлений при возврате на экран
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            mainViewModel.loadNotificationCount()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Assignment, contentDescription = null) },
                    label = { Text("Заявки") },
                    alwaysShowLabel = true
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        mainViewModel.loadNotificationCount()
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (notificationCount > 0) {
                                    Badge { Text(if (notificationCount > 99) "99+" else "$notificationCount") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null)
                        }
                    },
                    label = { Text("Уведомления") },
                    alwaysShowLabel = true
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Профиль") },
                    alwaysShowLabel = true
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                prefsMgr.pushNotificationPromptShown &&
                !FcmRegistrationHelper.hasNotificationPermission(context)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Включите push-уведомления в настройках системы, чтобы не пропускать заявки.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Настройки")
                        }
                    }
                }
            }
            when (selectedTab) {
                0 -> TicketListTab(
                    onTicketClick = onNavigateToTicket,
                    onCreateTicket = onNavigateToCreateTicket,
                    modifier = Modifier.fillMaxSize()
                )
                1 -> NotificationsTab(
                    onNavigateToTicket = onNavigateToTicket,
                    modifier = Modifier.fillMaxSize()
                )
                2 -> ProfileTab(
                    mainViewModel = mainViewModel,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToAdmin = onNavigateToAdmin,
                    onNavigateToChangePassword = onNavigateToChangePassword,
                    onLogout = {
                        mainViewModel.logout()
                        onLogout()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ===================== Profile Tab =====================

@Composable
fun ProfileTab(
    mainViewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Карточка профиля
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    mainViewModel.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (mainViewModel.username.isNotBlank()) {
                    Text(
                        "@${mainViewModel.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                val role = when {
                    mainViewModel.isSuperAdmin -> "Супер-администратор"
                    mainViewModel.isAdmin -> "Администратор"
                    else -> "Пользователь"
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text(role) },
                    icon = {
                        Icon(
                            when {
                                mainViewModel.isSuperAdmin -> Icons.Default.Shield
                                mainViewModel.isAdmin -> Icons.Default.AdminPanelSettings
                                else -> Icons.Default.Person
                            },
                            null,
                            Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Пункты меню
        if (mainViewModel.isAdmin || mainViewModel.isSuperAdmin) {
            ProfileMenuItem(
                icon = Icons.Default.AdminPanelSettings,
                title = "Админ-панель",
                subtitle = "Управление пользователями",
                onClick = onNavigateToAdmin
            )
        }

        ProfileMenuItem(
            icon = Icons.Default.Lock,
            title = "Сменить пароль",
            onClick = onNavigateToChangePassword
        )

        ProfileMenuItem(
            icon = Icons.Default.Dns,
            title = "Настройки сервера",
            subtitle = "Адрес подключения",
            onClick = onNavigateToSettings
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Выход
        OutlinedButton(
            onClick = { showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Logout, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Выйти из аккаунта")
        }

        // Информация о приложении
        Spacer(Modifier.height(32.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.SupportAgent, null, Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Управление заявками v1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                "IT-отдел университета",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    // Диалог подтверждения выхода
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Default.Logout, null) },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Вы будете перенаправлены на экран входа.") },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Отмена") }
            }
        )
    }
}
