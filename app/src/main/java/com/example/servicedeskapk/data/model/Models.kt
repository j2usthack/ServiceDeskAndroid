package com.example.servicedeskapk.data.model

import com.google.gson.annotations.SerializedName

// ============ Auth ============

data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val password: String,
    @SerializedName("password_confirm") val passwordConfirm: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String
)

data class ChangePasswordRequest(
    @SerializedName("new_password") val newPassword: String,
    @SerializedName("new_password_confirm") val newPasswordConfirm: String
)

data class AuthResponse(
    val success: Boolean? = null,
    val error: String? = null,
    val user: UserInfo? = null
)

data class UserInfo(
    val id: Long,
    val username: String?,
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("is_super_admin") val isSuperAdmin: Boolean = false,
    @SerializedName("must_change_password") val mustChangePassword: Boolean = false
) {
    val displayName: String
        get() {
            val name = listOfNotNull(firstName, lastName).joinToString(" ").trim()
            return name.ifEmpty { username ?: "Пользователь" }
        }
}

data class MeResponse(
    val user: UserInfo? = null,
    val error: String? = null
)

/** Регистрация FCM token на бэкенде (POST /api/user/device-token). */
data class DeviceTokenRequest(
    val token: String,
    val platform: String = "android"
)

/** Тело DELETE /api/user/device-token — снять регистрацию текущего устройства. */
data class DeviceTokenUnregisterRequest(
    val token: String
)

// ============ Tickets ============

data class TicketCreator(
    val id: Long?,
    val username: String?,
    val name: String?
)

data class TicketAssignee(
    val id: Long?,
    val username: String?,
    val name: String?
)

data class Ticket(
    val id: Int,
    val description: String,
    val room: String,
    val priority: String,
    @SerializedName("priority_name") val priorityName: String?,
    val status: String,
    @SerializedName("status_name") val statusName: String?,
    @SerializedName("category_root") val categoryRoot: String?,
    @SerializedName("category_sub") val categorySub: String?,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("closed_at") val closedAt: String?,
    @SerializedName("due_date") val dueDate: String?,
    @SerializedName("due_time") val dueTime: String?,
    val creator: TicketCreator?,
    val assignee: TicketAssignee?,
    val assignees: List<TicketAssignee>? = null,
    val resolution: String?,
    @SerializedName("is_subtask") val isSubtask: Boolean = false,
    @SerializedName("parent_ticket_id") val parentTicketId: Int? = null
)

data class TicketListResponse(
    val tickets: List<Ticket>,
    val total: Int,
    val page: Int,
    @SerializedName("per_page") val perPage: Int,
    val pages: Int
)

data class TicketDetailResponse(
    val ticket: Ticket? = null,
    val error: String? = null
)

data class CreateTicketRequest(
    val description: String,
    val room: String,
    val priority: String,
    @SerializedName("assignee_id") val assigneeId: Long? = null
)

data class CreateTicketResponse(
    val success: Boolean? = null,
    @SerializedName("ticket_id") val ticketId: Int? = null,
    val error: String? = null
)

data class UpdateTicketRequest(
    val status: String? = null,
    val description: String? = null,
    val room: String? = null,
    val priority: String? = null,
    @SerializedName("category_root") val categoryRoot: String? = null,
    @SerializedName("category_sub") val categorySub: String? = null,
    val resolution: String? = null,
    @SerializedName("assignee_id") val assigneeId: Long? = null,
    @SerializedName("due_date") val dueDate: String? = null,
    @SerializedName("due_time") val dueTime: String? = null,
    val comment: String? = null
)

data class SimpleResponse(
    val success: Boolean? = null,
    val error: String? = null,
    @SerializedName("pending_approval") val pendingApproval: Boolean? = null
)

// ============ Comments ============

data class Comment(
    val id: Long,
    @SerializedName("comment_text") val commentText: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("author_name") val authorName: String?
)

data class CommentsResponse(
    val comments: List<Comment>
)

data class AddCommentRequest(
    @SerializedName("comment_text") val commentText: String
)

// ============ Subtasks ============

data class Subtask(
    val id: Int,
    val description: String,
    val room: String,
    val priority: String,
    val status: String,
    @SerializedName("created_at") val createdAt: String?,
    val creator: TicketCreator?
)

data class SubtasksResponse(
    val subtasks: List<Subtask>
)

data class CreateSubtaskRequest(
    val description: String,
    val room: String,
    val priority: String
)

// ============ Users ============

data class User(
    val id: Long,
    val username: String?,
    val name: String?,
    @SerializedName("is_admin") val isAdmin: Boolean = false
)

data class UsersResponse(
    val users: List<User>
)

data class AdminUsersResponse(
    val users: List<AdminUser>,
    val total: Int,
    val page: Int,
    @SerializedName("per_page") val perPage: Int
)

data class AdminUser(
    @SerializedName("user_id") val userId: Long,
    val username: String?,
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?,
    @SerializedName("is_super_admin") val isSuperAdmin: Boolean = false
) {
    val displayName: String
        get() {
            val name = listOfNotNull(firstName, lastName).joinToString(" ").trim()
            return name.ifEmpty { username ?: "Пользователь" }
        }
}

// ============ Notifications ============

data class AppNotification(
    val id: Long,
    val title: String,
    val message: String?,
    val type: String?,
    @SerializedName("link_url") val linkUrl: String?,
    @SerializedName("is_read") val isRead: Int = 0,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("read_at") val readAt: String?
)

data class NotificationsResponse(
    val notifications: List<AppNotification>
)

data class NotificationCountResponse(
    val count: Int
)

// ============ Config ============

data class ConfigResponse(
    val categories: Map<String, String>,
    val priorities: Map<String, String>,
    val statuses: Map<String, String>,
    val user: ConfigUser?
)

data class ConfigUser(
    val id: Long,
    val username: String?,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("is_super_admin") val isSuperAdmin: Boolean = false
)

// ============ Statistics ============

data class StatisticsResponse(
    val total: Int?,
    @SerializedName("by_status") val byStatus: Map<String, Int>?,
    @SerializedName("by_priority") val byPriority: Map<String, Int>?,
    @SerializedName("by_category") val byCategory: Map<String, Int>?
)
