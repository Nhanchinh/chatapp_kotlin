package com.example.chatapp.data.remote.model

import com.squareup.moshi.Json

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
    @Json(name = "token_type") val tokenType: String
)


