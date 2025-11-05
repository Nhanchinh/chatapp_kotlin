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
    @Json(name = "birth_year") val birthYear: Int? = null
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


