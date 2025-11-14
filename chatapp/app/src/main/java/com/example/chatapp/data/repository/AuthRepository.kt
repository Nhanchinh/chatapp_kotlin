package com.example.chatapp.data.repository

import android.content.Context
import com.example.chatapp.data.encryption.CryptoManager
import com.example.chatapp.data.encryption.KeyManager
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.model.LoginResponse
import com.example.chatapp.data.remote.model.ProfileUpdateRequest
import com.example.chatapp.data.remote.model.RegisterRequest
import com.example.chatapp.data.remote.model.UserDto

class AuthRepository(context: Context) {
    private val api = ApiClient.apiService
    private val authManager = AuthManager(context)
    private val keyManager = KeyManager(context)

    suspend fun register(email: String, password: String, fullName: String): Result<Unit> {
        return try {
            // Generate RSA keypair for E2EE
            val publicKey = keyManager.generateAndStoreRSAKeyPair()
            val publicKeyBase64 = CryptoManager.encodePublicKey(publicKey)
            
            // Register with public key
            api.register(
                RegisterRequest(
                    email = email,
                    password = password,
                    fullName = fullName,
                    publicKey = publicKeyBase64
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(username: String, password: String): Result<LoginResponse> {
        return try {
            val response = api.login(username = username, password = password)
            authManager.saveAuthSessionWithRelativeExpiry(
                accessToken = response.accessToken,
                accessTokenExpiresInSeconds = response.expiresIn,
                refreshToken = response.refreshToken,
                refreshTokenExpiresInSeconds = response.refreshExpiresIn
            )
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
                
                // If user doesn't have RSA keypair yet (e.g., registered before E2EE), generate one
                if (!keyManager.hasRSAKeyPair()) {
                    try {
                        val publicKey = keyManager.generateAndStoreRSAKeyPair()
                        val publicKeyBase64 = CryptoManager.encodePublicKey(publicKey)
                        // Upload public key to server
                        val token = authManager.getValidAccessToken()
                        if (token != null) {
                            api.updateProfile(
                                "Bearer $token",
                                ProfileUpdateRequest(publicKey = publicKeyBase64)
                            )
                        }
                    } catch (e: Exception) {
                        // Log but don't fail login if key generation fails
                        android.util.Log.e("AuthRepository", "Failed to generate RSA keypair", e)
                    }
                }
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
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("No token"))
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

    suspend fun refreshAccessToken(): Result<String> {
        return try {
            val token = authManager.getValidAccessToken()
                ?: return Result.failure(Exception("Unable to refresh token"))
            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


