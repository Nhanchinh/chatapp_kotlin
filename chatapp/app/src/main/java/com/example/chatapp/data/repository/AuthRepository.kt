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
import com.example.chatapp.data.remote.model.OtpRequest
import com.example.chatapp.data.remote.model.VerifyOtpRequest
import com.example.chatapp.data.remote.model.ResetPasswordRequest
import com.example.chatapp.data.remote.model.ChangePasswordRequest
import org.json.JSONObject
import retrofit2.HttpException

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

    suspend fun requestPasswordOtp(email: String): Result<String> {
        return try {
            val response = api.requestPasswordOtp(OtpRequest(email))
            Result.success(response.message ?: "Vui lòng kiểm tra hộp thư của bạn")
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    suspend fun verifyPasswordOtp(email: String, otp: String): Result<String> {
        return try {
            val response = api.verifyPasswordOtp(VerifyOtpRequest(email, otp))
            if (response.success == true && !response.resetToken.isNullOrBlank()) {
                Result.success(response.resetToken)
            } else {
                Result.failure(Exception(response.message ?: "Xác minh OTP thất bại"))
            }
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    suspend fun resetPassword(email: String, newPassword: String, token: String): Result<Unit> {
        return try {
            val response = api.resetPassword(
                ResetPasswordRequest(email = email, newPassword = newPassword, token = token)
            )
            if (response.success == false) {
                Result.failure(Exception(response.message ?: "Không thể đặt lại mật khẩu"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> {
        return try {
            val token = authManager.getValidAccessToken()
                ?:
                return Result.failure(Exception("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."))
            val response = api.changePassword(
                "Bearer $token",
                ChangePasswordRequest(oldPassword = oldPassword, newPassword = newPassword)
            )
            if (response.success == false) {
                Result.failure(Exception(response.message ?: "Không thể đổi mật khẩu"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    private fun mapError(throwable: Throwable): Exception {
        if (throwable is HttpException) {
            val message = try {
                val errorBody = throwable.response()?.errorBody()?.string()
                extractErrorMessage(errorBody).ifEmpty { throwable.message() ?: "Đã xảy ra lỗi" }
            } catch (e: Exception) {
                throwable.message() ?: "Đã xảy ra lỗi"
            }
            return Exception(message)
        }
        return Exception(throwable.message ?: "Đã xảy ra lỗi")
    }

    private fun extractErrorMessage(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val json = JSONObject(raw)
            when {
                json.has("message") -> json.getString("message")
                json.has("detail") -> json.getString("detail")
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}


