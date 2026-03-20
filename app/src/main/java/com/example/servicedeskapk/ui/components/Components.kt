package com.example.servicedeskapk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.servicedeskapk.data.model.Ticket
import com.example.servicedeskapk.ui.theme.*
import java.util.Calendar
import java.util.TimeZone

// ===================== Date Utilities (Новосибирск, UTC+7) =====================

private val NSK_TZ = TimeZone.getTimeZone("Asia/Novosibirsk")

/** Серверная дата/время → отображаемый формат ("2026-02-11 14:30:00" → "11.02.2026 14:30") */
fun formatServerDateTime(serverDate: String): String {
    return try {
        if (serverDate.contains(" ")) {
            val parts = serverDate.split(" ")
            val d = parts[0].split("-")
            val t = parts.getOrNull(1)?.take(5) ?: ""
            if (d.size == 3) "${d[2]}.${d[1]}.${d[0]}" + (if (t.isNotBlank()) " $t" else "") else serverDate
        } else {
            val d = serverDate.split("-")
            if (d.size == 3 && d[0].length == 4) "${d[2]}.${d[1]}.${d[0]}" else serverDate
        }
    } catch (_: Exception) { serverDate }
}

/** Серверная дата → отображаемый формат ("2026-02-11" → "11.02.2026") */
fun serverDateToDisplay(serverDate: String): String {
    val d = serverDate.split("-")
    return if (d.size == 3 && d[0].length == 4) "${d[2]}.${d[1]}.${d[0]}" else serverDate
}

/** Отображаемая дата → серверный формат ("11.02.2026" → "2026-02-11") */
fun displayDateToServer(displayDate: String): String {
    val d = displayDate.split(".")
    return if (d.size == 3 && d[2].length == 4) "${d[2]}-${d[1].padStart(2, '0')}-${d[0].padStart(2, '0')}" else displayDate
}

/** Отображаемая дата ДД.ММ.ГГГГ → UTC millis для Material3 DatePicker */
fun displayDateToUtcMillis(displayDate: String): Long? {
    return try {
        val d = displayDate.split(".")
        if (d.size == 3) {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.clear()
            cal.set(d[2].toInt(), d[1].toInt() - 1, d[0].toInt())
            cal.timeInMillis
        } else null
    } catch (_: Exception) { null }
}

/** UTC millis из Material3 DatePicker → отображаемая дата ДД.ММ.ГГГГ */
fun utcMillisToDisplayDate(millis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    return String.format("%02d.%02d.%04d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
}

/** UTC millis начала сегодняшнего дня по Новосибирскому времени */
fun todayUtcMillisNsk(): Long {
    val nsk = Calendar.getInstance(NSK_TZ)
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.clear()
    utc.set(nsk.get(Calendar.YEAR), nsk.get(Calendar.MONTH), nsk.get(Calendar.DAY_OF_MONTH))
    return utc.timeInMillis
}

// ===================== Date / Time Pickers =====================

/** Поле даты с возможностью ручного ввода И выбора через DatePicker */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("ДД.ММ.ГГГГ") },
        trailingIcon = {
            IconButton(onClick = { if (enabled) showPicker = true }, enabled = enabled) {
                Icon(Icons.Default.CalendarToday, "Выбрать дату")
            }
        },
        supportingText = { Text("Нельзя выбрать дату раньше сегодняшнего дня") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled
    )

    if (showPicker) {
        val initialMillis = displayDateToUtcMillis(value) ?: todayUtcMillisNsk()
        val todayMillis = todayUtcMillisNsk()

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= todayMillis
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onValueChange(utcMillisToDisplayDate(millis))
                    }
                    showPicker = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** Поле времени с возможностью ручного ввода И выбора через TimePicker */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("ЧЧ:ММ") },
        trailingIcon = {
            IconButton(onClick = { if (enabled) showPicker = true }, enabled = enabled) {
                Icon(Icons.Default.Schedule, "Выбрать время")
            }
        },
        supportingText = { Text("Время Новосибирское (UTC+7)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled
    )

    if (showPicker) {
        val hour = try { value.split(":")[0].toInt().coerceIn(0, 23) } catch (_: Exception) { 12 }
        val minute = try { value.split(":")[1].toInt().coerceIn(0, 59) } catch (_: Exception) { 0 }

        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Выберите время (НСК)") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val h = timePickerState.hour.toString().padStart(2, '0')
                    val m = timePickerState.minute.toString().padStart(2, '0')
                    onValueChange("$h:$m")
                    showPicker = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Отмена") }
            }
        )
    }
}

// ===================== Color Utilities =====================

fun getPriorityColor(priority: String): Color = when (priority) {
    "urgent" -> PriorityUrgent
    "high" -> PriorityHigh
    "medium" -> PriorityMedium
    "low" -> PriorityLow
    else -> Color.Gray
}

fun getStatusColor(status: String): Color = when (status) {
    "open" -> StatusOpen
    "in_progress" -> StatusInProgress
    "waiting" -> StatusWaiting
    "closed" -> StatusClosed
    "reopened" -> StatusReopened
    "support_needed" -> StatusSupport
    else -> Color.Gray
}

// ===================== Ticket Card =====================

@Composable
fun TicketCard(
    ticket: Ticket,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val priorityColor = getPriorityColor(ticket.priority)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(priorityColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
            Column(modifier = Modifier.padding(12.dp).weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#${ticket.id}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(ticket.status, ticket.statusName)
                        PriorityChip(ticket.priority, ticket.priorityName)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(ticket.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (ticket.room.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Room, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(2.dp))
                            Text(ticket.room, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ticket.assignee?.let { a ->
                            val name = a.name ?: a.username ?: ""
                            if (name.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(2.dp))
                                    Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                        // Дата в формате ДД.ММ.ГГГГ
                        ticket.createdAt?.take(10)?.let { date ->
                            Text(serverDateToDisplay(date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Срок исполнения в формате ДД.ММ.ГГГГ
                ticket.dueDate?.let { dueDate ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(14.dp), tint = PriorityHigh)
                        Spacer(Modifier.width(4.dp))
                        val displayDate = serverDateToDisplay(dueDate)
                        val dueText = if (ticket.dueTime != null) "$displayDate ${ticket.dueTime}" else displayDate
                        Text("Срок: $dueText", style = MaterialTheme.typography.labelSmall, color = PriorityHigh, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ===================== Chips =====================

@Composable
fun PriorityChip(priority: String, priorityName: String?) {
    val color = getPriorityColor(priority)
    val label = when (priority) { "urgent" -> "Срочно"; "high" -> "Высокий"; "medium" -> "Средний"; "low" -> "Низкий"; else -> priorityName ?: priority }
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.15f)) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusChip(status: String, statusName: String?) {
    val color = getStatusColor(status)
    val label = when (status) { "open" -> "Открыта"; "in_progress" -> "В работе"; "waiting" -> "Ожидание"; "closed" -> "Закрыта"; "reopened" -> "Переоткрыта"; "support_needed" -> "Стор. спец."; else -> statusName ?: status }
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.15f)) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ===================== Section Header =====================

@Composable
fun SectionHeader(title: String, icon: ImageVector? = null, count: Int? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)) }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (count != null) { Spacer(Modifier.width(6.dp)); Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) { Text("$count", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer) } }
        }
        action?.invoke()
    }
}

// ===================== Profile Menu Item =====================

@Composable
fun ProfileMenuItem(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ===================== State Screens =====================

@Composable
fun LoadingScreen(message: String = "Загрузка...") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.ErrorOutline, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onRetry != null) { Spacer(Modifier.height(16.dp)); Button(onClick = onRetry) { Text("Повторить") } }
        }
    }
}

@Composable
fun EmptyState(message: String, icon: @Composable () -> Unit = { Icon(Icons.Default.Inbox, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            icon()
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}
