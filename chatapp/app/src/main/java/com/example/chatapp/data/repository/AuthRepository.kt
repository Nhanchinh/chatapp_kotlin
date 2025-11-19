package com.example.chatapp.data.repository

import android.content.Context
import android.util.Log
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
            // Public key will be provisioned on first login (requires_public_key flag)
            api.register(
                RegisterRequest(
                    email = email,
                    password = password,
                    fullName = fullName,
                    publicKey = null
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
                val userId = user.id
                if (!userId.isNullOrBlank()) {
                    keyManager.setActiveUser(userId)
                }
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
                
                ensurePublicKeySynced(response.requiresPublicKey == true)
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ensure client has RSA keypair and server stores matching public key.
     * If requiresPublicKey is true, upload current public key even if keypair exists.
     */
    private suspend fun ensurePublicKeySynced(serverRequiresPublicKey: Boolean) {
        var needsUpload = false
        var publicKeyBase64: String? = null

        try {
            if (!keyManager.hasRSAKeyPair()) {
                val publicKey = keyManager.generateAndStoreRSAKeyPair()
                publicKeyBase64 = CryptoManager.encodePublicKey(publicKey)
                needsUpload = true
            }

            if (serverRequiresPublicKey) {
                if (publicKeyBase64 == null) {
                    publicKeyBase64 = keyManager.getRSAPublicKeyBase64()
                }
                if (publicKeyBase64 == null) {
                    val regeneratedKey = keyManager.generateAndStoreRSAKeyPair()
                    publicKeyBase64 = CryptoManager.encodePublicKey(regeneratedKey)
                }
                needsUpload = true
            }

            if (needsUpload && publicKeyBase64 != null) {
                val token = authManager.getValidAccessToken()
                if (token != null) {
                    api.updateProfile(
                        "Bearer $token",
                        ProfileUpdateRequest(publicKey = publicKeyBase64)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to sync public key", e)
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


