package com.example.chatapp.data.repository

import android.content.Context
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.model.LoginResponse
import com.example.chatapp.data.remote.model.ProfileUpdateRequest
import com.example.chatapp.data.remote.model.RegisterRequest
import com.example.chatapp.data.remote.model.UserDto

class AuthRepository(context: Context) {
    private val api = ApiClient.apiService
    private val authManager = AuthManager(context)

    suspend fun register(email: String, password: String, fullName: String): Result<Unit> {
        return try {
            api.register(RegisterRequest(email = email, password = password, fullName = fullName))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(username: String, password: String): Result<LoginResponse> {
        return try {
            val response = api.login(username = username, password = password)
            val expiryTime = response.expiresIn?.let { System.currentTimeMillis() + it * 1000 }
            authManager.saveAccessToken(response.accessToken, expiryTime)
            response.user?.let { user ->
                authManager.saveUserProfile(
                    id = user.id,
                    email = user.email,
                    fullName = user.fullName,
                    role = user.role,
                    friendCount = user.friendCount,
                    location = user.location,
                    hometown = user.hometown,
                    birthYear = user.birthYear
                )
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        fullName: String? = null,
        location: String? = null,
        hometown: String? = null,
        birthYear: Int? = null
    ): Result<UserDto> {
        return try {
            val token = authManager.getAccessTokenOnce() ?: return Result.failure(Exception("No token"))
            val request = ProfileUpdateRequest(
                fullName = fullName,
                location = location,
                hometown = hometown,
                birthYear = birthYear
            )
            val updatedUser = api.updateProfile("Bearer $token", request)
            // Save updated profile
            authManager.saveUserProfile(
                id = updatedUser.id,
                email = updatedUser.email,
                fullName = updatedUser.fullName,
                role = updatedUser.role,
                friendCount = updatedUser.friendCount,
                location = updatedUser.location,
                hometown = updatedUser.hometown,
                birthYear = updatedUser.birthYear
            )
            Result.success(updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


