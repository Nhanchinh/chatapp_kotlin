package com.example.chatapp.data.remote.model

import com.squareup.moshi.Json

data class NotificationDto(
    @Json(name = "_id") val id: String,
    @Json(name = "user_id") val userId: String,
    val title: String,
    val body: String,
    val type: String, // "friend_request", "friend_accept", "friend_decline", "friend_cancel", "unfriend", "system"
    @Json(name = "from_user_id") val fromUserId: String? = null,
    @Json(name = "from_user_name") val fromUserName: String? = null,
    val data: Map<String, String>? = null,
    @Json(name = "is_read") val isRead: Boolean,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "expires_at") val expiresAt: String? = null
)

data class NotificationsResponse(
    val items: List<NotificationDto> = emptyList(),
    @Json(name = "next_cursor") val nextCursor: String? = null
)

data class NotificationCountResponse(
    val count: Int
)

data class NotificationActionResponse(
    val message: String
)

