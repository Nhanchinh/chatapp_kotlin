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
    @Json(name = "last_seen") val lastSeen: String? = null,
    @Json(name = "public_key") val publicKey: String? = null  // RSA public key for E2EE
)

data class RegisterRequest(
    val email: String,
    val password: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "public_key") val publicKey: String? = null  // RSA public key for E2EE
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
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "refresh_expires_in") val refreshExpiresIn: Long? = null,
    val user: UserDto? = null,
    @Json(name = "requires_public_key") val requiresPublicKey: Boolean? = null
)

data class ProfileUpdateRequest(
    @Json(name = "full_name") val fullName: String? = null,
    val location: String? = null,
    val hometown: String? = null,
    @Json(name = "birth_year") val birthYear: Int? = null,
    @Json(name = "public_key") val publicKey: String? = null  // RSA public key for E2EE
)

data class SearchUsersResponse(
    val items: List<UserDto> = emptyList()
)

data class RefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String
)

data class RefreshTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "refresh_expires_in") val refreshExpiresIn: Long? = null
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

// ========== E2EE Key Exchange Models ==========

data class PublicKeyDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "public_key") val publicKey: String?,
    @Json(name = "full_name") val fullName: String?
)

data class PublicKeysResponse(
    val items: List<PublicKeyDto> = emptyList()
)

data class EncryptedSessionKeyDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "encrypted_session_key") val encryptedSessionKey: String
)

data class StoreKeysRequest(
    @Json(name = "conversation_id") val conversationId: String,
    val keys: List<EncryptedSessionKeyDto>
)

data class StoreKeysResponse(
    val message: String,
    @Json(name = "conversation_id") val conversationId: String
)

data class GetKeyResponse(
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "encrypted_session_key") val encryptedSessionKey: String
)

// ===== Password & OTP =====

data class OtpRequest(
    val email: String
)

data class SimpleMessageResponse(
    val message: String? = null,
    val success: Boolean? = null
)

data class VerifyOtpRequest(
    val email: String,
    val otp: String
)

data class VerifyOtpResponse(
    val success: Boolean?,
    val message: String?,
    @Json(name = "reset_token") val resetToken: String?,
    @Json(name = "expires_in") val expiresIn: Int?
)

data class ResetPasswordRequest(
    val email: String,
    @Json(name = "new_password") val newPassword: String,
    val token: String
)

data class ChangePasswordRequest(
    @Json(name = "old_password") val oldPassword: String,
    @Json(name = "new_password") val newPassword: String
)
