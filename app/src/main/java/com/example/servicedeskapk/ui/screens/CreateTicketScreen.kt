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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.servicedeskapk.ServiceDeskApp
import com.example.servicedeskapk.data.api.RetrofitClient
import com.example.servicedeskapk.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ===================== ViewModel =====================

class CreateTicketViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as ServiceDeskApp).preferencesManager

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    data class State(
        val description: String = "",
        val room: String = "",
        val priority: String = "medium",
        val assigneeId: Long? = null,
        val categoryRoot: String = "",
        val categorySub: String = "",
        val admins: List<User> = emptyList(),
        val categories: Map<String, String> = emptyMap(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val createdTicketId: Int? = null
    )

    init {
        loadAdmins()
        loadConfig()
    }

    private fun loadAdmins() {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                val response = api.getUsers(role = "admins")
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(admins = response.body()?.users ?: emptyList())
                }
            } catch (_: Exception) { }
        }
    }

    private fun loadConfig() {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                val response = api.getConfig()
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(categories = response.body()?.categories ?: emptyMap())
                }
            } catch (_: Exception) { }
        }
    }

    fun onFieldChanged(field: String, value: String) {
        _state.value = when (field) {
            "description" -> _state.value.copy(description = value, error = null)
            "room" -> _state.value.copy(room = value, error = null)
            "priority" -> _state.value.copy(priority = value, error = null)
            "categoryRoot" -> _state.value.copy(categoryRoot = value, categorySub = "", error = null)
            "categorySub" -> _state.value.copy(categorySub = value, error = null)
            else -> _state.value
        }
    }

    fun onAssigneeChanged(assigneeId: Long?) {
        _state.value = _state.value.copy(assigneeId = assigneeId, error = null)
    }

    fun createTicket() {
        val s = _state.value
        if (s.description.isBlank()) { _state.value = s.copy(error = "Введите описание заявки"); return }
        if (s.room.isBlank()) { _state.value = s.copy(error = "Укажите аудиторию/кабинет"); return }

        viewModelScope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                val response = api.createTicket(
                    CreateTicketRequest(
                        description = s.description.trim(),
                        room = s.room.trim(),
                        priority = s.priority,
                        assigneeId = s.assigneeId
                    )
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val ticketId = response.body()?.ticketId

                    // Если выбрана категория, обновляем через update
                    if (ticketId != null && (s.categoryRoot.isNotBlank() || s.categorySub.isNotBlank())) {
                        try {
                            api.updateTicket(ticketId, UpdateTicketRequest(
                                categoryRoot = s.categoryRoot.takeIf { it.isNotBlank() },
                                categorySub = s.categorySub.takeIf { it.isNotBlank() }
                            ))
                        } catch (_: Exception) { }
                    }

                    _state.value = _state.value.copy(isLoading = false, createdTicketId = ticketId)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val msg = try {
                        com.google.gson.Gson().fromJson(errorBody, CreateTicketResponse::class.java)?.error
                    } catch (_: Exception) { null }
                    _state.value = _state.value.copy(isLoading = false, error = msg ?: response.body()?.error ?: "Ошибка создания")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Ошибка подключения: ${e.localizedMessage}")
            }
        }
    }
}

// ===================== Screen =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketScreen(
    onTicketCreated: (ticketId: Int?) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CreateTicketViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var priorityExpanded by remember { mutableStateOf(false) }
    var assigneeExpanded by remember { mutableStateOf(false) }
    var categoryRootExpanded by remember { mutableStateOf(false) }
    var categorySubExpanded by remember { mutableStateOf(false) }

    val rootCategories = state.categories.filter { !it.key.contains('.') }.toList()
    val subCategories = if (state.categoryRoot.isNotEmpty()) state.categories.filter { it.key.startsWith("${state.categoryRoot}.") }.toList() else emptyList()

    LaunchedEffect(state.createdTicketId) {
        state.createdTicketId?.let { onTicketCreated(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая заявка") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Описание
            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.onFieldChanged("description", it) },
                label = { Text("Описание проблемы *") },
                leadingIcon = { Icon(Icons.Default.Description, null) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                enabled = !state.isLoading,
                supportingText = { Text("Опишите проблему подробно") }
            )

            Spacer(Modifier.height(8.dp))

            // Аудитория
            OutlinedTextField(
                value = state.room,
                onValueChange = { viewModel.onFieldChanged("room", it) },
                label = { Text("Аудитория / Кабинет *") },
                leadingIcon = { Icon(Icons.Default.Room, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            )

            Spacer(Modifier.height(8.dp))

            // Приоритет
            val priorities = listOf(
                "urgent" to "Срочно (критичная проблема)",
                "high" to "Высокий (работа затруднена)",
                "medium" to "Средний (можно подождать)",
                "low" to "Низкий (не срочно)"
            )
            ExposedDropdownMenuBox(expanded = priorityExpanded, onExpandedChange = { priorityExpanded = it }) {
                OutlinedTextField(
                    value = priorities.firstOrNull { it.first == state.priority }?.second ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Приоритет") },
                    leadingIcon = { Icon(Icons.Default.PriorityHigh, null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(priorityExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    enabled = !state.isLoading
                )
                ExposedDropdownMenu(expanded = priorityExpanded, onDismissRequest = { priorityExpanded = false }) {
                    priorities.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.onFieldChanged("priority", value); priorityExpanded = false })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Категория (корневая)
            if (rootCategories.isNotEmpty()) {
                ExposedDropdownMenuBox(expanded = categoryRootExpanded, onExpandedChange = { categoryRootExpanded = it }) {
                    OutlinedTextField(
                        value = state.categories[state.categoryRoot] ?: "Не выбрана",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Категория") },
                        leadingIcon = { Icon(Icons.Default.Category, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryRootExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        enabled = !state.isLoading
                    )
                    ExposedDropdownMenu(expanded = categoryRootExpanded, onDismissRequest = { categoryRootExpanded = false }) {
                        DropdownMenuItem(text = { Text("Не выбрана") }, onClick = { viewModel.onFieldChanged("categoryRoot", ""); categoryRootExpanded = false })
                        rootCategories.forEach { (key, value) ->
                            DropdownMenuItem(text = { Text(value) }, onClick = { viewModel.onFieldChanged("categoryRoot", key); categoryRootExpanded = false })
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // Подкатегория
            if (subCategories.isNotEmpty()) {
                ExposedDropdownMenuBox(expanded = categorySubExpanded, onExpandedChange = { categorySubExpanded = it }) {
                    OutlinedTextField(
                        value = state.categories[state.categorySub] ?: "Не выбрана",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Подкатегория") },
                        leadingIcon = { Icon(Icons.Default.Label, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categorySubExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        enabled = !state.isLoading
                    )
                    ExposedDropdownMenu(expanded = categorySubExpanded, onDismissRequest = { categorySubExpanded = false }) {
                        DropdownMenuItem(text = { Text("Не выбрана") }, onClick = { viewModel.onFieldChanged("categorySub", ""); categorySubExpanded = false })
                        subCategories.forEach { (key, value) ->
                            DropdownMenuItem(text = { Text(value) }, onClick = { viewModel.onFieldChanged("categorySub", key); categorySubExpanded = false })
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // Исполнитель
            if (state.admins.isNotEmpty()) {
                ExposedDropdownMenuBox(expanded = assigneeExpanded, onExpandedChange = { assigneeExpanded = it }) {
                    val selectedAdmin = state.admins.firstOrNull { it.id == state.assigneeId }
                    val selectedText = selectedAdmin?.let { it.name?.takeIf { n -> n.isNotBlank() } ?: it.username ?: "" } ?: "Не назначен"

                    OutlinedTextField(
                        value = selectedText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Исполнитель") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(assigneeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        enabled = !state.isLoading,
                        supportingText = {
                            if (state.assigneeId == null) {
                                Text("Уведомление получат все администраторы")
                            }
                        }
                    )
                    ExposedDropdownMenu(expanded = assigneeExpanded, onDismissRequest = { assigneeExpanded = false }) {
                        DropdownMenuItem(text = { Text("Не назначен (уведомить всех)") }, onClick = { viewModel.onAssigneeChanged(null); assigneeExpanded = false })
                        state.admins.forEach { admin ->
                            val name = admin.name?.takeIf { it.isNotBlank() } ?: admin.username ?: "ID ${admin.id}"
                            DropdownMenuItem(text = { Text(name) }, onClick = { viewModel.onAssigneeChanged(admin.id); assigneeExpanded = false })
                        }
                    }
                }
            }

            // Ошибка
            state.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Кнопка создания
            Button(
                onClick = viewModel::createTicket,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !state.isLoading,
                shape = MaterialTheme.shapes.medium
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Send, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Создать заявку")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
