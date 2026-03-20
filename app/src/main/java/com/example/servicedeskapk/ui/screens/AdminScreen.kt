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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.servicedeskapk.ServiceDeskApp
import com.example.servicedeskapk.data.api.RetrofitClient
import com.example.servicedeskapk.data.model.AdminUser
import com.example.servicedeskapk.data.model.SimpleResponse
import com.example.servicedeskapk.data.model.StatisticsResponse
import com.example.servicedeskapk.ui.components.LoadingScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as ServiceDeskApp).preferencesManager

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    val isSuperAdmin get() = prefs.isSuperAdmin
    val currentUserId get() = prefs.userId

    data class State(
        val users: List<AdminUser> = emptyList(),
        val admins: List<AdminUser> = emptyList(),
        val stats: StatisticsResponse? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val actionMessage: String? = null,
        val selectedTab: Int = 0,
        val usersPage: Int = 1,
        val usersTotalPages: Int = 1,
        val adminsPage: Int = 1,
        val adminsTotalPages: Int = 1
    )

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)

                // Загружаем статистику
                val statsResp = api.getStatistics()
                val stats = if (statsResp.isSuccessful) statsResp.body() else null

                // Загружаем пользователей
                val usersResp = api.getAdminUsers(page = 1, perPage = 20, role = "users")
                val users = if (usersResp.isSuccessful) {
                    val body = usersResp.body()
                    _state.value = _state.value.copy(
                        usersTotalPages = ((body?.total ?: 0) + 19) / 20
                    )
                    body?.users ?: emptyList()
                } else emptyList()

                // Загружаем администраторов
                val adminsResp = api.getAdminUsers(page = 1, perPage = 20, role = "admins")
                val admins = if (adminsResp.isSuccessful) {
                    val body = adminsResp.body()
                    _state.value = _state.value.copy(
                        adminsTotalPages = ((body?.total ?: 0) + 19) / 20
                    )
                    body?.users ?: emptyList()
                } else emptyList()

                _state.value = _state.value.copy(
                    stats = stats,
                    users = users,
                    admins = admins,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Ошибка: ${e.localizedMessage}"
                )
            }
        }
    }

    fun onTabSelected(tab: Int) {
        _state.value = _state.value.copy(selectedTab = tab)
    }

    fun promoteUser(userId: Long) {
        performAction(userId, "promote") { api ->
            api.promoteUser(userId)
        }
    }

    fun demoteUser(userId: Long) {
        // Нельзя снять права у самого себя
        if (userId == currentUserId) {
            _state.value = _state.value.copy(actionMessage = "Вы не можете снять права администратора у самого себя")
            return
        }
        // Нельзя снять права у супер-администратора
        val targetAdmin = _state.value.admins.find { it.userId == userId }
        if (targetAdmin?.isSuperAdmin == true) {
            _state.value = _state.value.copy(actionMessage = "Нельзя снять права у супер-администратора")
            return
        }
        performAction(userId, "demote") { api ->
            api.demoteUser(userId)
        }
    }

    fun resetPassword(userId: Long) {
        performAction(userId, "reset-password") { api ->
            api.resetPassword(userId)
        }
    }

    fun blockUser(userId: Long) {
        performAction(userId, "block") { api ->
            api.blockUser(userId)
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(actionMessage = null)
    }

    private fun performAction(
        userId: Long,
        actionName: String,
        action: suspend (com.example.servicedeskapk.data.api.ApiService) -> retrofit2.Response<SimpleResponse>
    ) {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                val response = action(api)
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(actionMessage = "Действие выполнено")
                    loadData()
                } else {
                    _state.value = _state.value.copy(
                        actionMessage = "Ошибка выполнения действия"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    actionMessage = "Ошибка: ${e.localizedMessage}"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Админ-панель") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (state.isLoading) {
            LoadingScreen()
            return@Scaffold
        }

        Column(modifier = Modifier.padding(paddingValues)) {
            // Табы
            TabRow(selectedTabIndex = state.selectedTab) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.onTabSelected(0) },
                    text = { Text("Статистика") },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) }
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.onTabSelected(1) },
                    text = { Text("Пользователи") },
                    icon = { Icon(Icons.Default.People, contentDescription = null) }
                )
                Tab(
                    selected = state.selectedTab == 2,
                    onClick = { viewModel.onTabSelected(2) },
                    text = { Text("Админы") },
                    icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) }
                )
            }

            when (state.selectedTab) {
                0 -> StatisticsTab(state.stats)
                1 -> UsersTab(
                    users = state.users,
                    isSuperAdmin = viewModel.isSuperAdmin,
                    onPromote = viewModel::promoteUser,
                    onBlock = viewModel::blockUser,
                    onResetPassword = viewModel::resetPassword
                )
                2 -> AdminsTab(
                    admins = state.admins,
                    isSuperAdmin = viewModel.isSuperAdmin,
                    currentUserId = viewModel.currentUserId,
                    onDemote = viewModel::demoteUser,
                    onResetPassword = viewModel::resetPassword
                )
            }
        }
    }
}

@Composable
private fun StatisticsTab(stats: StatisticsResponse?) {
    if (stats == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет данных", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Всего заявок",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${stats.total ?: 0}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        stats.byStatus?.let { byStatus ->
            item {
                Text(
                    "По статусам",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val statusNames = mapOf(
                            "open" to "Открыта",
                            "in_progress" to "В работе",
                            "waiting" to "Ожидание",
                            "closed" to "Закрыта",
                            "reopened" to "Переоткрыта",
                            "support_needed" to "Стор. специалист"
                        )
                        byStatus.forEach { (status, count) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(statusNames[status] ?: status)
                                Text("$count", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        stats.byPriority?.let { byPriority ->
            item {
                Text(
                    "По приоритетам",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val priorityNames = mapOf(
                            "urgent" to "Срочно",
                            "high" to "Высокий",
                            "medium" to "Средний",
                            "low" to "Низкий"
                        )
                        byPriority.forEach { (priority, count) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(priorityNames[priority] ?: priority)
                                Text("$count", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersTab(
    users: List<AdminUser>,
    isSuperAdmin: Boolean,
    onPromote: (Long) -> Unit,
    onBlock: (Long) -> Unit,
    onResetPassword: (Long) -> Unit
) {
    if (users.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет пользователей", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(users, key = { it.userId }) { user ->
            UserItem(user = user) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isSuperAdmin) {
                            AssistChip(
                                onClick = { onPromote(user.userId) },
                                label = { Text("В админы") },
                                leadingIcon = { Icon(Icons.Default.ArrowUpward, null, Modifier.size(16.dp)) }
                            )
                        }
                        AssistChip(
                            onClick = { onResetPassword(user.userId) },
                            label = { Text("Сброс пароля") },
                            leadingIcon = { Icon(Icons.Default.LockReset, null, Modifier.size(16.dp)) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = { onBlock(user.userId) },
                            label = { Text("Заблокировать") },
                            leadingIcon = { Icon(Icons.Default.Block, null, Modifier.size(16.dp)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminsTab(
    admins: List<AdminUser>,
    isSuperAdmin: Boolean,
    currentUserId: Long,
    onDemote: (Long) -> Unit,
    onResetPassword: (Long) -> Unit
) {
    if (admins.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет администраторов", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(admins, key = { it.userId }) { admin ->
            val isSelf = admin.userId == currentUserId
            val isAdminSuperAdmin = admin.isSuperAdmin

            UserItem(user = admin, badge = if (isAdminSuperAdmin) "Супер-админ" else null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Показываем кнопку "Снять админа" только если:
                    // 1) Текущий пользователь — супер-админ
                    // 2) Это НЕ он сам
                    // 3) Целевой админ НЕ является супер-админом
                    if (isSuperAdmin && !isSelf && !isAdminSuperAdmin) {
                        AssistChip(
                            onClick = { onDemote(admin.userId) },
                            label = { Text("Снять админа") },
                            leadingIcon = { Icon(Icons.Default.ArrowDownward, null, Modifier.size(16.dp)) }
                        )
                    }
                    AssistChip(
                        onClick = { onResetPassword(admin.userId) },
                        label = { Text("Сброс пароля") },
                        leadingIcon = { Icon(Icons.Default.LockReset, null, Modifier.size(16.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserItem(
    user: AdminUser,
    badge: String? = null,
    actions: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            user.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (badge != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    badge,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    user.username?.let {
                        Text(
                            "@$it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "ID: ${user.userId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            actions()
        }
    }
}
