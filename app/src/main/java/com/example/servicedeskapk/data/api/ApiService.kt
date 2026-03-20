package com.example.servicedeskapk.data.api

import com.example.servicedeskapk.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // ============ Auth ============

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<SimpleResponse>

    @GET("api/auth/me")
    suspend fun me(): Response<MeResponse>

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<SimpleResponse>

    /** Регистрация FCM device token (после входа / onNewToken). */
    @POST("api/user/device-token")
    suspend fun registerDeviceToken(@Body body: DeviceTokenRequest): Response<SimpleResponse>

    /** Снятие регистрации FCM для этого устройства (перед logout). */
    @HTTP(method = "DELETE", path = "api/user/device-token", hasBody = true)
    suspend fun unregisterDeviceToken(@Body body: DeviceTokenUnregisterRequest): Response<SimpleResponse>

    // ============ Config ============

    @GET("api/config")
    suspend fun getConfig(): Response<ConfigResponse>

    // ============ Tickets ============

    @GET("api/tickets")
    suspend fun getTickets(
        @Query("status") status: String? = null,
        @Query("priority") priority: String? = null,
        @Query("category") category: String? = null,
        @Query("assignee_id") assigneeId: Int? = null,
        @Query("creator_id") creatorId: Int? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("sort") sort: String? = null
    ): Response<TicketListResponse>

    @GET("api/ticket/{id}")
    suspend fun getTicket(@Path("id") ticketId: Int): Response<TicketDetailResponse>

    @POST("api/ticket/create")
    suspend fun createTicket(@Body request: CreateTicketRequest): Response<CreateTicketResponse>

    @POST("api/ticket/{id}/update")
    suspend fun updateTicket(
        @Path("id") ticketId: Int,
        @Body request: UpdateTicketRequest
    ): Response<SimpleResponse>

    @POST("api/ticket/{id}/delete")
    suspend fun deleteTicket(@Path("id") ticketId: Int): Response<SimpleResponse>

    @POST("api/ticket/{id}/take")
    suspend fun takeTicket(@Path("id") ticketId: Int): Response<SimpleResponse>

    @POST("api/ticket/{id}/take-coassignee")
    suspend fun takeCoassignee(@Path("id") ticketId: Int): Response<SimpleResponse>

    // ============ Comments ============

    @GET("api/ticket/{id}/comments")
    suspend fun getComments(@Path("id") ticketId: Int): Response<CommentsResponse>

    @POST("api/ticket/{id}/comments")
    suspend fun addComment(
        @Path("id") ticketId: Int,
        @Body request: AddCommentRequest
    ): Response<SimpleResponse>

    // ============ Subtasks ============

    @GET("api/ticket/{id}/subtasks")
    suspend fun getSubtasks(@Path("id") ticketId: Int): Response<SubtasksResponse>

    @POST("api/ticket/{id}/subtasks")
    suspend fun createSubtask(
        @Path("id") ticketId: Int,
        @Body request: CreateSubtaskRequest
    ): Response<SimpleResponse>

    // ============ Users ============

    @GET("api/users")
    suspend fun getUsers(@Query("role") role: String? = null): Response<UsersResponse>

    // ============ Admin ============

    @GET("api/admin/users")
    suspend fun getAdminUsers(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 10,
        @Query("role") role: String = "users"
    ): Response<AdminUsersResponse>

    @POST("api/admin/users/{id}/promote")
    suspend fun promoteUser(@Path("id") userId: Long): Response<SimpleResponse>

    @POST("api/admin/users/{id}/demote")
    suspend fun demoteUser(@Path("id") userId: Long): Response<SimpleResponse>

    @POST("api/admin/users/{id}/reset-password")
    suspend fun resetPassword(@Path("id") userId: Long): Response<SimpleResponse>

    @POST("api/admin/users/{id}/block")
    suspend fun blockUser(@Path("id") userId: Long): Response<SimpleResponse>

    @POST("api/admin/users/{id}/delete")
    suspend fun deleteUser(@Path("id") userId: Long): Response<SimpleResponse>

    // ============ Notifications ============

    @GET("api/notifications/count")
    suspend fun getNotificationCount(): Response<NotificationCountResponse>

    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("limit") limit: Int = 50,
        @Query("unread_only") unreadOnly: String = "false"
    ): Response<NotificationsResponse>

    @POST("api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") notificationId: Long): Response<SimpleResponse>

    @POST("api/notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<SimpleResponse>

    @POST("api/notifications/clear")
    suspend fun clearNotifications(): Response<SimpleResponse>

    // ============ Statistics ============

    @GET("api/statistics")
    suspend fun getStatistics(): Response<StatisticsResponse>

    // ============ Assign / Reassign ============

    @POST("api/ticket/{id}/assign")
    suspend fun assignTicket(
        @Path("id") ticketId: Int,
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): Response<SimpleResponse>

    @POST("api/ticket/{id}/request-reassign")
    suspend fun requestReassign(
        @Path("id") ticketId: Int,
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): Response<SimpleResponse>
}
