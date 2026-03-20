package com.example.servicedeskapk.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.servicedeskapk.ServiceDeskApp
import com.example.servicedeskapk.data.api.RetrofitClient
import com.example.servicedeskapk.data.model.AppNotification
import com.example.servicedeskapk.ui.components.EmptyState
import com.example.servicedeskapk.ui.components.ErrorScreen
import com.example.servicedeskapk.ui.components.LoadingScreen
import com.example.servicedeskapk.ui.components.formatServerDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ===================== ViewModel =====================

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as ServiceDeskApp).preferencesManager

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    data class State(
        val notifications: List<AppNotification> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )

    init { loadNotifications() }

    fun loadNotifications() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                val response = api.getNotifications(limit = 100)
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        notifications = response.body()?.notifications ?: emptyList(),
                        isLoading = false
                    )
                } else {
                    _state.value = _state.value.copy(isLoading = false, error = "Ошибка загрузки (${response.code()})")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Ошибка подключения: ${e.localizedMessage}")
            }
        }
    }

    fun markAsRead(notificationId: Long) {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                api.markNotificationRead(notificationId)
                _state.value = _state.value.copy(
                    notifications = _state.value.notifications.map {
                        if (it.id == notificationId) it.copy(isRead = 1) else it
                    }
                )
            } catch (_: Exception) { }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                api.markAllNotificationsRead()
                _state.value = _state.value.copy(
                    notifications = _state.value.notifications.map { it.copy(isRead = 1) }
                )
            } catch (_: Exception) { }
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                api.clearNotifications()
                loadNotifications()
            } catch (_: Exception) { }
        }
    }

    companion object {
        fun extractTicketId(linkUrl: String?): Int? {
            if (linkUrl.isNullOrBlank()) return null
            return Regex("""/ticket/(\d+)""").find(linkUrl)?.groupValues?.get(1)?.toIntOrNull()
        }
    }
}

// ===================== Tab Content (для MainScreen) =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsTab(
    onNavigateToTicket: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Уведомления") },
                actions = {
                    IconButton(onClick = { viewModel.loadNotifications() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Ещё")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Прочитать все") },
                                leadingIcon = { Icon(Icons.Default.DoneAll, null) },
                                onClick = { showMenu = false; viewModel.markAllRead() }
                            )
                            DropdownMenuItem(
                                text = { Text("Очистить прочитанные") },
                                leadingIcon = { Icon(Icons.Default.ClearAll, null) },
                                onClick = { showMenu = false; viewModel.clearNotifications() }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        NotificationsContent(
            state = state,
            viewModel = viewModel,
            onNavigateToTicket = onNavigateToTicket,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

// ===================== Standalone Screen (для прямой навигации) =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTicket: (Int) -> Unit,
    viewModel: NotificationsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Уведомления") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadNotifications() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Ещё")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Прочитать все") },
                                leadingIcon = { Icon(Icons.Default.DoneAll, null) },
                                onClick = { showMenu = false; viewModel.markAllRead() }
                            )
                            DropdownMenuItem(
                                text = { Text("Очистить прочитанные") },
                                leadingIcon = { Icon(Icons.Default.ClearAll, null) },
                                onClick = { showMenu = false; viewModel.clearNotifications() }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        NotificationsContent(
            state = state,
            viewModel = viewModel,
            onNavigateToTicket = onNavigateToTicket,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

// ===================== Shared Content =====================

@Composable
private fun NotificationsContent(
    state: NotificationsViewModel.State,
    viewModel: NotificationsViewModel,
    onNavigateToTicket: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        state.isLoading -> LoadingScreen()
        state.error != null -> ErrorScreen(message = state.error, onRetry = { viewModel.loadNotifications() })
        state.notifications.isEmpty() -> {
            EmptyState(
                message = "Нет уведомлений",
                icon = {
                    Icon(
                        Icons.Default.NotificationsNone, null, Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            )
        }
        else -> {
            val unreadCount = state.notifications.count { it.isRead == 0 }

            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Индикатор непрочитанных
                if (unreadCount > 0) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "Непрочитанных: $unreadCount",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                items(state.notifications, key = { it.id }) { notification ->
                    NotificationItem(
                        notification = notification,
                        onClick = {
                            if (notification.isRead == 0) viewModel.markAsRead(notification.id)
                            val ticketId = NotificationsViewModel.extractTicketId(notification.linkUrl)
                            if (ticketId != null) onNavigateToTicket(ticketId)
                        }
                    )
                }
            }
        }
    }
}

// ===================== Notification Item =====================

@Composable
private fun NotificationItem(
    notification: AppNotification,
    onClick: () -> Unit
) {
    val isUnread = notification.isRead == 0
    val hasLink = NotificationsViewModel.extractTicketId(notification.linkUrl) != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnread) 2.dp else 0.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (isUnread) {
                        Icon(Icons.Default.Circle, null, Modifier.size(8.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = notification.createdAt?.let { formatServerDateTime(it) } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            notification.message?.let { message ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (hasLink) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.OpenInNew, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Открыть заявку", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
