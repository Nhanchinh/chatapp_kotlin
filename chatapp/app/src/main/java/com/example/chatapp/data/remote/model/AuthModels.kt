package com.example.chatapp.data.remote.model

import com.squareup.moshi.Json

data class UserDto(
    val id: String?,
    val email: String?,
    @Json(name = "full_name") val fullName: String?,
    val role: String?,
    @Json(name = "friend_count") val friendCount: Int? = null,
    val location: String? = null,
    val hometown: String? = null,
    @Json(name = "birth_year") val birthYear: Int? = null,
    @Json(name = "is_online") val isOnline: Boolean? = null,
    @Json(name = "last_seen") val lastSeen: String? = null
)

data class RegisterRequest(
    val email: String,
    val password: String,
    @Json(name = "full_name") val fullName: String
)

data class RegisterResponse(
    val id: String? = null,
    val email: String? = null,
    val message: String? = null
)

data class LoginResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    val user: UserDto? = null
)

data class ProfileUpdateRequest(
    @Json(name = "full_name") val fullName: String? = null,
    val location: String? = null,
    val hometown: String? = null,
    @Json(name = "birth_year") val birthYear: Int? = null
)

data class SearchUsersResponse(
    val items: List<UserDto> = emptyList()
)

data class FriendActionResponse(
    val msg: String? = null
)

data class FriendsListResponse(
    val friends: List<UserDto> = emptyList()
)

data class FriendRequestDto(
    val id: String?,
    @Json(name = "from_user") val fromUser: String?,
    @Json(name = "to_user") val toUser: String?,
    val status: String?,
    @Json(name = "created_at") val createdAt: String?,
    val requester: UserDto? = null
)

data class FriendRequestsResponse(
    val requests: List<FriendRequestDto> = emptyList()
)

data class PresenceResponse(
    @Json(name = "user_id") val userId: String,
    val online: Boolean,
    @Json(name = "last_seen") val lastSeen: String? = null
)

data class PresenceDto(
    @Json(name = "user_id") val userId: String,
    val online: Boolean,
    @Json(name = "last_seen") val lastSeen: String? = null
)

data class BatchPresenceResponse(
    val presences: List<PresenceDto> = emptyList()
)


