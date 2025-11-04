package com.example.chatapp.data.repository

import android.content.Context
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.model.RegisterRequest

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

    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            val response = api.login(username = username, password = password)
            // No expiry from backend; optionally you can add if returned later
            authManager.saveAccessToken(response.accessToken, null)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


