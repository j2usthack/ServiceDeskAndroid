package com.example.servicedeskapk.ui.screens

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.servicedeskapk.ServiceDeskApp
import com.example.servicedeskapk.data.api.RetrofitClient
import com.example.servicedeskapk.data.model.Ticket
import com.example.servicedeskapk.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ===================== ViewModel =====================

class TicketListViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as ServiceDeskApp).preferencesManager

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    val isAdmin get() = prefs.isAdmin
    val isSuperAdmin get() = prefs.isSuperAdmin
    val displayName get() = prefs.displayName

    data class State(
        val tickets: List<Ticket> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val statusFilter: String? = null,
        val priorityFilter: String? = null,
        val searchQuery: String = "",
        val currentPage: Int = 1,
        val totalPages: Int = 1,
        val totalTickets: Int = 0
    ) {
        val hasActiveFilters get() = statusFilter != null || priorityFilter != null
    }

    init {
        loadTickets()
    }

    fun setStatusFilter(status: String?) {
        _state.value = _state.value.copy(statusFilter = status, currentPage = 1)
        loadTickets()
    }

    fun setPriorityFilter(priority: String?) {
        _state.value = _state.value.copy(priorityFilter = priority, currentPage = 1)
        loadTickets()
    }

    fun clearFilters() {
        _state.value = _state.value.copy(statusFilter = null, priorityFilter = null, currentPage = 1)
        loadTickets()
    }

    fun onSearchChanged(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun search() {
        _state.value = _state.value.copy(currentPage = 1)
        loadTickets()
    }

    fun refresh() {
        _state.value = _state.value.copy(isRefreshing = true, currentPage = 1)
        loadTickets()
    }

    fun loadNextPage() {
        val s = _state.value
        if (s.currentPage < s.totalPages && !s.isLoading) {
            _state.value = s.copy(currentPage = s.currentPage + 1)
            loadTickets(append = true)
        }
    }

    fun loadTickets(append: Boolean = false) {
        viewModelScope.launch {
            val s = _state.value
            if (!append) {
                _state.value = s.copy(isLoading = true, error = null)
            }
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                val response = api.getTickets(
                    status = s.statusFilter,
                    priority = s.priorityFilter,
                    search = s.searchQuery.takeIf { it.isNotBlank() },
                    page = s.currentPage,
                    perPage = 20
                )
                if (response.isSuccessful) {
                    val body = response.body()!!
                    val tickets = if (append) _state.value.tickets + body.tickets else body.tickets
                    _state.value = _state.value.copy(
                        tickets = tickets,
                        totalPages = body.pages,
                        totalTickets = body.total,
                        isLoading = false,
                        isRefreshing = false,
                        error = null
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = "Ошибка загрузки (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = "Ошибка подключения: ${e.localizedMessage}"
                )
            }
        }
    }
}

// ===================== Tab Content =====================

/**
 * Контент вкладки «Заявки» для MainScreen.
 * Включает собственный Scaffold с TopAppBar, фильтрами и FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketListTab(
    onTicketClick: (Int) -> Unit,
    onCreateTicket: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TicketListViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (showSearch) {
                SearchTopBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::onSearchChanged,
                    onSearch = {
                        viewModel.search()
                        showSearch = false
                    },
                    onClose = {
                        showSearch = false
                        if (state.searchQuery.isNotEmpty()) {
                            viewModel.onSearchChanged("")
                            viewModel.search()
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Заявки")
                            if (state.totalTickets > 0) {
                                Text(
                                    "Всего: ${state.totalTickets}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, "Поиск")
                        }
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(
                                if (state.hasActiveFilters) Icons.Default.FilterAlt else Icons.Default.FilterList,
                                "Фильтры",
                                tint = if (state.hasActiveFilters) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, "Обновить")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateTicket,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Создать")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // Сворачиваемые фильтры
            AnimatedVisibility(visible = showFilters) {
                FiltersSection(
                    selectedStatus = state.statusFilter,
                    selectedPriority = state.priorityFilter,
                    isAdmin = viewModel.isAdmin,
                    isSuperAdmin = viewModel.isSuperAdmin,
                    onStatusSelected = viewModel::setStatusFilter,
                    onPrioritySelected = viewModel::setPriorityFilter,
                    onClearFilters = {
                        viewModel.clearFilters()
                        showFilters = false
                    }
                )
            }

            // Индикатор активных фильтров
            if (state.hasActiveFilters && !showFilters) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FilterAlt, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("Фильтры активны", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { viewModel.clearFilters() }, contentPadding = PaddingValues(0.dp)) {
                            Text("Сбросить", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Контент
            when {
                state.isLoading && state.tickets.isEmpty() -> LoadingScreen()
                state.error != null && state.tickets.isEmpty() -> ErrorScreen(
                    message = state.error!!,
                    onRetry = { viewModel.loadTickets() }
                )
                state.tickets.isEmpty() -> EmptyState(
                    message = if (state.hasActiveFilters || state.searchQuery.isNotBlank())
                        "Ничего не найдено" else "Заявок пока нет",
                    icon = {
                        Icon(
                            if (state.hasActiveFilters) Icons.Default.FilterListOff else Icons.Default.Inbox,
                            null, Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                )
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.tickets, key = { it.id }) { ticket ->
                            TicketCard(ticket = ticket, onClick = { onTicketClick(ticket.id) })
                        }
                        if (state.currentPage < state.totalPages) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    OutlinedButton(onClick = { viewModel.loadNextPage() }) {
                                        Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Загрузить ещё")
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) } // Отступ под FAB
                    }
                }
            }
        }
    }
}

// ===================== Search Bar =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Поиск заявок...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Очистить")
                        }
                    }
                }
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Закрыть") }
        },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Поиск") }
        }
    )
}

// ===================== Filters =====================

@Composable
private fun FiltersSection(
    selectedStatus: String?,
    selectedPriority: String?,
    isAdmin: Boolean,
    isSuperAdmin: Boolean,
    onStatusSelected: (String?) -> Unit,
    onPrioritySelected: (String?) -> Unit,
    onClearFilters: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // === Статус ===
            Text("Статус", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))

            val statusFilters = buildList {
                add(null to "Все")
                if (isAdmin || isSuperAdmin) {
                    add("my" to "Мои")
                    add("open" to "Открытые")
                }
                if (isSuperAdmin) {
                    add("in_progress" to "В работе")
                }
                add("closed" to "Закрытые")
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                statusFilters.forEach { (status, label) ->
                    FilterChip(
                        selected = selectedStatus == status,
                        onClick = { onStatusSelected(if (selectedStatus == status) null else status) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // === Приоритет ===
            Text("Приоритет", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))

            val priorities = listOf(
                null to "Все",
                "urgent" to "Срочно",
                "high" to "Высокий",
                "medium" to "Средний",
                "low" to "Низкий"
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                priorities.forEach { (priority, label) ->
                    FilterChip(
                        selected = selectedPriority == priority,
                        onClick = { onPrioritySelected(if (selectedPriority == priority) null else priority) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            // Кнопка сброса
            if (selectedStatus != null || selectedPriority != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onClearFilters, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.ClearAll, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Сбросить фильтры", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
