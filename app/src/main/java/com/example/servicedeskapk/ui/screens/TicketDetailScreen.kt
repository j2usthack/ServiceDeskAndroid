package com.example.servicedeskapk.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.example.servicedeskapk.data.model.*
import com.example.servicedeskapk.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ===================== ViewModel =====================

class TicketDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as ServiceDeskApp).preferencesManager

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    val isAdmin get() = prefs.isAdmin
    val isSuperAdmin get() = prefs.isSuperAdmin
    val userId get() = prefs.userId

    data class State(
        val ticket: Ticket? = null,
        val comments: List<Comment> = emptyList(),
        val subtasks: List<Subtask> = emptyList(),
        val admins: List<User> = emptyList(),
        val categories: Map<String, String> = emptyMap(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val commentText: String = "",
        val isSendingComment: Boolean = false,
        val actionMessage: String? = null,
        val isPerformingAction: Boolean = false
    )

    fun loadTicket(ticketId: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                val ticketResp = api.getTicket(ticketId)
                val commentsResp = api.getComments(ticketId)
                val subtasksResp = api.getSubtasks(ticketId)
                val adminsResp = api.getUsers(role = "admins")
                val configResp = api.getConfig()

                if (ticketResp.isSuccessful && ticketResp.body()?.ticket != null) {
                    _state.value = _state.value.copy(
                        ticket = ticketResp.body()!!.ticket,
                        comments = commentsResp.body()?.comments ?: emptyList(),
                        subtasks = subtasksResp.body()?.subtasks ?: emptyList(),
                        admins = adminsResp.body()?.users ?: emptyList(),
                        categories = configResp.body()?.categories ?: emptyMap(),
                        isLoading = false
                    )
                } else {
                    _state.value = _state.value.copy(isLoading = false, error = ticketResp.body()?.error ?: "Заявка не найдена")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Ошибка подключения: ${e.localizedMessage}")
            }
        }
    }

    fun onCommentChanged(text: String) { _state.value = _state.value.copy(commentText = text) }

    fun sendComment(ticketId: Int) {
        val text = _state.value.commentText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSendingComment = true)
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                val response = api.addComment(ticketId, AddCommentRequest(text))
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(commentText = "", isSendingComment = false)
                    val commentsResp = api.getComments(ticketId)
                    if (commentsResp.isSuccessful) _state.value = _state.value.copy(comments = commentsResp.body()?.comments ?: emptyList())
                } else {
                    _state.value = _state.value.copy(isSendingComment = false, actionMessage = "Не удалось отправить комментарий")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSendingComment = false, actionMessage = "Ошибка: ${e.localizedMessage}")
            }
        }
    }

    fun takeTicket(ticketId: Int) = performAction("Заявка взята в работу") {
        val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
        val r = api.takeTicket(ticketId); if (!r.isSuccessful) throw Exception(parseError(r) ?: "Ошибка"); loadTicket(ticketId)
    }

    fun takeCoassignee(ticketId: Int) = performAction("Вы стали соисполнителем") {
        val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
        val r = api.takeCoassignee(ticketId); if (!r.isSuccessful) throw Exception(parseError(r) ?: "Ошибка"); loadTicket(ticketId)
    }

    fun updateStatus(ticketId: Int, status: String, comment: String? = null) = performAction("Статус обновлён") {
        val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
        val r = api.updateTicket(ticketId, UpdateTicketRequest(status = status, comment = comment)); if (!r.isSuccessful) throw Exception(parseError(r) ?: "Ошибка"); loadTicket(ticketId)
    }

    fun updateTicketFields(ticketId: Int, request: UpdateTicketRequest) = performAction("Заявка обновлена") {
        val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
        val r = api.updateTicket(ticketId, request); if (!r.isSuccessful) throw Exception(parseError(r) ?: "Ошибка"); loadTicket(ticketId)
    }

    fun assignTicket(ticketId: Int, assigneeId: Long) = performAction("Исполнитель назначен") {
        val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
        val r = api.assignTicket(ticketId, mapOf<String, Any>("action" to "add", "assignee_id" to assigneeId, "set_as_primary" to true))
        if (!r.isSuccessful) throw Exception(parseError(r) ?: "Ошибка"); loadTicket(ticketId)
    }

    fun createSubtask(parentId: Int, desc: String, room: String, priority: String) = performAction("Подзадача создана") {
        val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
        val r = api.createSubtask(parentId, CreateSubtaskRequest(desc, room, priority)); if (!r.isSuccessful) throw Exception(parseError(r) ?: "Ошибка"); loadTicket(parentId)
    }

    fun deleteTicket(ticketId: Int, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isPerformingAction = true)
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                val r = api.deleteTicket(ticketId)
                if (r.isSuccessful) onDeleted()
                else _state.value = _state.value.copy(isPerformingAction = false, actionMessage = "Не удалось удалить")
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPerformingAction = false, actionMessage = "Ошибка: ${e.localizedMessage}")
            }
        }
    }

    fun clearActionMessage() { _state.value = _state.value.copy(actionMessage = null) }

    private fun performAction(msg: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isPerformingAction = true)
            try { action(); _state.value = _state.value.copy(isPerformingAction = false, actionMessage = msg) }
            catch (e: Exception) { _state.value = _state.value.copy(isPerformingAction = false, actionMessage = "Ошибка: ${e.message}") }
        }
    }

    private fun <T> parseError(response: retrofit2.Response<T>): String? {
        return try { com.google.gson.Gson().fromJson(response.errorBody()?.string(), SimpleResponse::class.java)?.error } catch (_: Exception) { null }
    }
}

// ===================== Screen =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    ticketId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToTicket: (Int) -> Unit,
    viewModel: TicketDetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showActionsMenu by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSubtaskDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var showDueDateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(ticketId) { viewModel.loadTicket(ticketId) }
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearActionMessage() }
    }

    val ticket = state.ticket
    val canTake = ticket != null && (viewModel.isAdmin || viewModel.isSuperAdmin) && ticket.status == "open"
    val isAssigned = ticket?.assignee?.id == viewModel.userId || ticket?.assignees?.any { it.id == viewModel.userId } == true
    val canCoassignee = ticket != null && (viewModel.isAdmin || viewModel.isSuperAdmin) && !isAssigned && ticket.assignee != null && ticket.status !in listOf("closed", "support_needed")
    val canEdit = ticket != null && (viewModel.isAdmin || viewModel.isSuperAdmin || ticket.creator?.id == viewModel.userId || isAssigned)
    val canAssign = viewModel.isAdmin || viewModel.isSuperAdmin
    val isClosed = ticket?.status in listOf("closed", "support_needed")
    val actionInProgress = state.isPerformingAction

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заявка #$ticketId") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (ticket != null) {
                        // Индикатор загрузки действия
                        if (actionInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(end = 4.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(onClick = { viewModel.loadTicket(ticketId) }, enabled = !actionInProgress) {
                            Icon(Icons.Default.Refresh, "Обновить")
                        }

                        Box {
                            IconButton(onClick = { showActionsMenu = true }, enabled = !actionInProgress) {
                                Icon(Icons.Default.MoreVert, "Действия")
                            }
                            DropdownMenu(expanded = showActionsMenu, onDismissRequest = { showActionsMenu = false }) {
                                if (canTake) {
                                    DropdownMenuItem(
                                        text = { Text("Взять в работу") },
                                        leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                                        onClick = { showActionsMenu = false; viewModel.takeTicket(ticketId) },
                                        enabled = !actionInProgress
                                    )
                                }
                                if (canCoassignee) {
                                    DropdownMenuItem(
                                        text = { Text("Стать соисполнителем") },
                                        leadingIcon = { Icon(Icons.Default.GroupAdd, null) },
                                        onClick = { showActionsMenu = false; viewModel.takeCoassignee(ticketId) },
                                        enabled = !actionInProgress
                                    )
                                }
                                if (canEdit) {
                                    DropdownMenuItem(
                                        text = { Text("Изменить статус") },
                                        leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                                        onClick = { showActionsMenu = false; showStatusDialog = true },
                                        enabled = !actionInProgress
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Редактировать") },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                        onClick = { showActionsMenu = false; showEditDialog = true },
                                        enabled = !actionInProgress
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Срок исполнения") },
                                        leadingIcon = { Icon(Icons.Default.CalendarToday, null) },
                                        onClick = { showActionsMenu = false; showDueDateDialog = true },
                                        enabled = !actionInProgress
                                    )
                                }
                                if (canAssign) {
                                    DropdownMenuItem(
                                        text = { Text("Назначить исполнителя") },
                                        leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                                        onClick = { showActionsMenu = false; showAssignDialog = true },
                                        enabled = !actionInProgress
                                    )
                                }
                                if (canEdit && !isClosed) {
                                    DropdownMenuItem(
                                        text = { Text("Добавить решение") },
                                        leadingIcon = { Icon(Icons.Default.CheckCircle, null) },
                                        onClick = { showActionsMenu = false; showResolutionDialog = true },
                                        enabled = !actionInProgress
                                    )
                                }
                                if ((canAssign || canEdit) && ticket.isSubtask.not()) {
                                    DropdownMenuItem(
                                        text = { Text("Создать подзадачу") },
                                        leadingIcon = { Icon(Icons.Default.AddTask, null) },
                                        onClick = { showActionsMenu = false; showSubtaskDialog = true },
                                        enabled = !actionInProgress
                                    )
                                }
                                if (canAssign) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Удалить заявку", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { showActionsMenu = false; showDeleteDialog = true },
                                        enabled = !actionInProgress
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingScreen()
            state.error != null -> ErrorScreen(state.error!!, onRetry = { viewModel.loadTicket(ticketId) })
            ticket != null -> {
                LazyColumn(
                    modifier = Modifier.padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // === Кнопка «Взять в работу» ===
                    if (canTake) {
                        item {
                            Button(
                                onClick = { viewModel.takeTicket(ticketId) },
                                enabled = !actionInProgress,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (actionInProgress) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Выполняется...")
                                } else {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Взять в работу")
                                }
                            }
                        }
                    }

                    // === Заголовок ===
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    StatusChip(ticket.status, ticket.statusName)
                                    PriorityChip(ticket.priority, ticket.priorityName)
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(ticket.description, style = MaterialTheme.typography.bodyLarge)
                                if (ticket.isSubtask && ticket.parentTicketId != null) {
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = { onNavigateToTicket(ticket.parentTicketId) }, contentPadding = PaddingValues(0.dp)) {
                                        Icon(Icons.Default.SubdirectoryArrowLeft, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Родительская заявка #${ticket.parentTicketId}")
                                    }
                                }
                            }
                        }
                    }

                    // === Информация (даты в ДД.ММ.ГГГГ) ===
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                InfoRow(Icons.Default.Room, "Аудитория", ticket.room)
                                ticket.categoryName?.let { if (it.isNotBlank() && it != "Неизвестная категория") InfoRow(Icons.Default.Category, "Категория", it) }
                                ticket.creator?.let { InfoRow(Icons.Default.PersonOutline, "Создатель", it.name ?: it.username ?: "") }
                                ticket.assignee?.let { InfoRow(Icons.Default.Person, "Исполнитель", it.name ?: it.username ?: "") }
                                ticket.assignees?.let { list ->
                                    if (list.size > 1) InfoRow(Icons.Default.Groups, "Соисполнители", list.mapNotNull { a -> a.name ?: a.username }.joinToString(", "))
                                }
                                ticket.createdAt?.let { InfoRow(Icons.Default.CalendarToday, "Создана", formatServerDateTime(it)) }
                                ticket.dueDate?.let { dd ->
                                    val display = serverDateToDisplay(dd)
                                    val full = if (ticket.dueTime != null) "$display ${ticket.dueTime}" else display
                                    InfoRow(Icons.Default.Schedule, "Срок исполнения", full)
                                }
                                ticket.closedAt?.let { InfoRow(Icons.Default.CheckCircle, "Закрыта", formatServerDateTime(it)) }
                            }
                        }
                    }

                    // === Решение ===
                    ticket.resolution?.let { resolution ->
                        if (resolution.isNotBlank()) {
                            item {
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.TaskAlt, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Решение", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(resolution, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }

                    // === Подзадачи ===
                    if (state.subtasks.isNotEmpty()) {
                        item { SectionHeader("Подзадачи", Icons.Default.Checklist, state.subtasks.size) }
                        items(state.subtasks) { subtask -> SubtaskItem(subtask) { onNavigateToTicket(subtask.id) } }
                    }

                    // === Комментарии ===
                    item { SectionHeader("Комментарии", Icons.AutoMirrored.Filled.Chat, state.comments.size) }
                    if (state.comments.isEmpty()) {
                        item { Text("Комментариев пока нет", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp)) }
                    } else {
                        items(state.comments) { comment -> CommentItem(comment) }
                    }

                    // === Ввод комментария ===
                    item { CommentInput(state.commentText, viewModel::onCommentChanged, { viewModel.sendComment(ticketId) }, state.isSendingComment) }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    // === Диалоги ===
    if (showStatusDialog) {
        StatusChangeDialog(ticket?.status ?: "", onSelect = { showStatusDialog = false; viewModel.updateStatus(ticketId, it) }, onDismiss = { showStatusDialog = false })
    }
    if (showDeleteDialog) {
        DeleteConfirmDialog(onConfirm = { showDeleteDialog = false; viewModel.deleteTicket(ticketId) { onNavigateBack() } }, onDismiss = { showDeleteDialog = false })
    }
    if (showEditDialog && ticket != null) {
        EditTicketDialog(ticket, state.categories, onSave = { showEditDialog = false; viewModel.updateTicketFields(ticketId, it) }, onDismiss = { showEditDialog = false })
    }
    if (showSubtaskDialog) {
        CreateSubtaskDialog(onSave = { d, r, p -> showSubtaskDialog = false; viewModel.createSubtask(ticketId, d, r, p) }, onDismiss = { showSubtaskDialog = false })
    }
    if (showAssignDialog) {
        AssignDialog(state.admins, ticket?.assignee?.id, onAssign = { showAssignDialog = false; viewModel.assignTicket(ticketId, it) }, onDismiss = { showAssignDialog = false })
    }
    if (showResolutionDialog) {
        ResolutionDialog(ticket?.resolution ?: "", onSave = { showResolutionDialog = false; viewModel.updateTicketFields(ticketId, UpdateTicketRequest(resolution = it)) }, onDismiss = { showResolutionDialog = false })
    }
    if (showDueDateDialog && ticket != null) {
        SetDueDateDialog(
            currentDate = ticket.dueDate,
            currentTime = ticket.dueTime,
            onSave = { req -> showDueDateDialog = false; viewModel.updateTicketFields(ticketId, req) },
            onDismiss = { showDueDateDialog = false }
        )
    }
}

// ===================== UI Components =====================

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SubtaskItem(subtask: Subtask, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(subtask.status, null)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("#${subtask.id}: ${subtask.description}", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Text("Кабинет: ${subtask.room}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            PriorityChip(subtask.priority, null)
        }
    }
}

@Composable
private fun CommentItem(comment: Comment) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(comment.authorName ?: "Пользователь", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                // Дата комментария в формате ДД.ММ.ГГГГ ЧЧ:ММ
                Text(comment.createdAt?.let { formatServerDateTime(it) } ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(comment.commentText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CommentInput(text: String, onChanged: (String) -> Unit, onSend: () -> Unit, isSending: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(
            value = text, onValueChange = onChanged, placeholder = { Text("Написать комментарий...") },
            modifier = Modifier.weight(1f), maxLines = 4, enabled = !isSending, shape = MaterialTheme.shapes.medium
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = onSend, enabled = text.isNotBlank() && !isSending, modifier = Modifier.size(48.dp)) {
            if (isSending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Icon(Icons.AutoMirrored.Filled.Send, "Отправить")
        }
    }
}

// ===================== Dialogs =====================

@Composable
private fun StatusChangeDialog(currentStatus: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val statuses = listOf("open" to "Открыта", "in_progress" to "В работе", "waiting" to "Ожидание", "closed" to "Закрыта", "support_needed" to "Стор. специалист")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SwapHoriz, null) },
        title = { Text("Изменить статус") },
        text = {
            Column {
                statuses.forEach { (status, label) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = currentStatus == status, onClick = { onSelect(status) })
                        Spacer(Modifier.width(8.dp))
                        StatusChip(status, label)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Удалить заявку?") },
        text = { Text("Заявка будет помечена как удалённая.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Диалог установки срока исполнения (как на web-версии) */
@Composable
private fun SetDueDateDialog(
    currentDate: String?,
    currentTime: String?,
    onSave: (UpdateTicketRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var date by remember { mutableStateOf(currentDate?.let { serverDateToDisplay(it) } ?: "") }
    var time by remember { mutableStateOf(currentTime ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CalendarToday, null) },
        title = { Text("Установить срок исполнения") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DatePickerField(label = "Дата", value = date, onValueChange = { date = it })
                TimePickerField(label = "Время (опционально)", value = time, onValueChange = { time = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val serverDate = if (date.isNotBlank()) displayDateToServer(date.trim()) else null
                    val timeStr = time.trim().takeIf { it.isNotBlank() }
                    onSave(UpdateTicketRequest(dueDate = serverDate, dueTime = timeStr))
                },
                enabled = date.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (currentDate != null) {
                    TextButton(onClick = {
                        onSave(UpdateTicketRequest(dueDate = "", dueTime = ""))
                    }) { Text("Очистить", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTicketDialog(ticket: Ticket, categories: Map<String, String>, onSave: (UpdateTicketRequest) -> Unit, onDismiss: () -> Unit) {
    var description by remember { mutableStateOf(ticket.description) }
    var room by remember { mutableStateOf(ticket.room) }
    var priority by remember { mutableStateOf(ticket.priority) }
    // Даты в формате ДД.ММ.ГГГГ для отображения
    var dueDate by remember { mutableStateOf(ticket.dueDate?.let { serverDateToDisplay(it) } ?: "") }
    var dueTime by remember { mutableStateOf(ticket.dueTime ?: "") }
    var selectedCategoryRoot by remember { mutableStateOf(ticket.categoryRoot ?: "") }
    var selectedCategorySub by remember { mutableStateOf(ticket.categorySub ?: "") }
    var priorityExpanded by remember { mutableStateOf(false) }
    var categoryRootExpanded by remember { mutableStateOf(false) }
    var categorySubExpanded by remember { mutableStateOf(false) }

    val priorities = listOf("urgent" to "Срочно", "high" to "Высокий", "medium" to "Средний", "low" to "Низкий")
    val rootCategories = categories.filter { !it.key.contains('.') }.toList()
    val subCategories = if (selectedCategoryRoot.isNotEmpty()) categories.filter { it.key.startsWith("$selectedCategoryRoot.") }.toList() else emptyList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать заявку") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Аудитория") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                ExposedDropdownMenuBox(expanded = priorityExpanded, onExpandedChange = { priorityExpanded = it }) {
                    OutlinedTextField(value = priorities.firstOrNull { it.first == priority }?.second ?: "", onValueChange = {}, readOnly = true, label = { Text("Приоритет") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(priorityExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = priorityExpanded, onDismissRequest = { priorityExpanded = false }) {
                        priorities.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { priority = v; priorityExpanded = false }) }
                    }
                }
                if (rootCategories.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = categoryRootExpanded, onExpandedChange = { categoryRootExpanded = it }) {
                        OutlinedTextField(value = categories[selectedCategoryRoot] ?: "Не выбрана", onValueChange = {}, readOnly = true, label = { Text("Категория") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryRootExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                        ExposedDropdownMenu(expanded = categoryRootExpanded, onDismissRequest = { categoryRootExpanded = false }) {
                            DropdownMenuItem(text = { Text("Не выбрана") }, onClick = { selectedCategoryRoot = ""; selectedCategorySub = ""; categoryRootExpanded = false })
                            rootCategories.forEach { (k, v) -> DropdownMenuItem(text = { Text(v) }, onClick = { selectedCategoryRoot = k; selectedCategorySub = ""; categoryRootExpanded = false }) }
                        }
                    }
                }
                if (subCategories.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = categorySubExpanded, onExpandedChange = { categorySubExpanded = it }) {
                        OutlinedTextField(value = categories[selectedCategorySub] ?: "Не выбрана", onValueChange = {}, readOnly = true, label = { Text("Подкатегория") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categorySubExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                        ExposedDropdownMenu(expanded = categorySubExpanded, onDismissRequest = { categorySubExpanded = false }) {
                            DropdownMenuItem(text = { Text("Не выбрана") }, onClick = { selectedCategorySub = ""; categorySubExpanded = false })
                            subCategories.forEach { (k, v) -> DropdownMenuItem(text = { Text(v) }, onClick = { selectedCategorySub = k; categorySubExpanded = false }) }
                        }
                    }
                }
                // Срок исполнения с DatePicker и TimePicker
                DatePickerField(label = "Срок (дата)", value = dueDate, onValueChange = { dueDate = it })
                TimePickerField(label = "Срок (время)", value = dueTime, onValueChange = { dueTime = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Конвертируем дату из ДД.ММ.ГГГГ в серверный формат ГГГГ-ММ-ДД
                val serverDueDate = if (dueDate.isNotBlank()) displayDateToServer(dueDate.trim()) else ""
                onSave(UpdateTicketRequest(
                    description = description.trim().takeIf { it != ticket.description },
                    room = room.trim().takeIf { it != ticket.room },
                    priority = priority.takeIf { it != ticket.priority },
                    categoryRoot = selectedCategoryRoot.takeIf { it != (ticket.categoryRoot ?: "") }?.ifEmpty { null },
                    categorySub = selectedCategorySub.takeIf { it != (ticket.categorySub ?: "") }?.ifEmpty { null },
                    dueDate = serverDueDate.takeIf { it.isNotEmpty() && it != (ticket.dueDate ?: "") },
                    dueTime = dueTime.trim().takeIf { it.isNotEmpty() && it != (ticket.dueTime ?: "") }
                ))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSubtaskDialog(onSave: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var description by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("medium") }
    var expanded by remember { mutableStateOf(false) }
    val priorities = listOf("urgent" to "Срочно", "high" to "Высокий", "medium" to "Средний", "low" to "Низкий")

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AddTask, null) },
        title = { Text("Новая подзадача") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Описание *") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Аудитория *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(value = priorities.firstOrNull { it.first == priority }?.second ?: "", onValueChange = {}, readOnly = true, label = { Text("Приоритет") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        priorities.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { priority = v; expanded = false }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (description.isNotBlank() && room.isNotBlank()) onSave(description.trim(), room.trim(), priority) }, enabled = description.isNotBlank() && room.isNotBlank()) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AssignDialog(admins: List<User>, currentId: Long?, onAssign: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PersonAdd, null) },
        title = { Text("Назначить исполнителя") },
        text = {
            Column {
                if (admins.isEmpty()) Text("Нет доступных администраторов")
                else admins.forEach { admin ->
                    val name = admin.name?.takeIf { it.isNotBlank() } ?: admin.username ?: "ID ${admin.id}"
                    val isCurrent = admin.id == currentId
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isCurrent, onClick = { if (!isCurrent) onAssign(admin.id) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                            if (isCurrent) Text("Текущий", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun ResolutionDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var resolution by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.TaskAlt, null) },
        title = { Text("Решение заявки") },
        text = {
            Column {
                Text("При сохранении решения заявка будет автоматически закрыта.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = resolution, onValueChange = { resolution = it }, label = { Text("Решение") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6)
            }
        },
        confirmButton = { TextButton(onClick = { if (resolution.isNotBlank()) onSave(resolution.trim()) }, enabled = resolution.isNotBlank()) { Text("Сохранить и закрыть") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
