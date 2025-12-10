package com.example.chatapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.encryption.KeyManager
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.repository.AuthRepository
import com.example.chatapp.util.FCMManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthState(
    val isLoggedIn: Boolean = false,
    val accessToken: String? = null,
    val isLoading: Boolean = false,
    val isInitialized: Boolean = false,
    val userId: String? = null,
    val userEmail: String? = null,
    val userFullName: String? = null,
    val userRole: String? = null,
    val userFriendCount: Int? = null,
    val userLocation: String? = null,
    val userHometown: String? = null,
    val userBirthYear: Int? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val authManager = AuthManager(application)
    private val repository = AuthRepository(application)
    private val keyManager = KeyManager(application)
    private val fcmManager = FCMManager(application, ApiClient.apiService)

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    companion object {
        private const val TAG = "AuthViewModel"
    }

    init {
        // Load saved state on initialization
        viewModelScope.launch {
            loadAuthState()
        }
    }

    private suspend fun loadAuthState() {
        val wasLoggedIn = authManager.isLoggedInOnce()
        val token = if (wasLoggedIn) authManager.getValidAccessToken() else null
        val isLoggedIn = token != null && authManager.isLoggedInOnce()
        val user = if (isLoggedIn) authManager.getUserProfileOnce() else null

        // Set active user in KeyManager if user is logged in
        // This ensures keys are accessible when app restarts
        if (isLoggedIn && user?.id != null) {
            keyManager.setActiveUser(user.id)
        } else {
            keyManager.setActiveUser(null)
        }

        _authState.value = AuthState(
            isLoggedIn = isLoggedIn,
            accessToken = if (isLoggedIn) token else null,
            isInitialized = true,
            userId = user?.id,
            userEmail = user?.email,
            userFullName = user?.fullName,
            userRole = user?.role,
            userFriendCount = user?.friendCount,
            userLocation = user?.location,
            userHometown = user?.hometown,
            userBirthYear = user?.birthYear
        )
        
        // ✅ FIX: Register FCM token when restoring session
        if (isLoggedIn && token != null) {
            Log.d(TAG, "🔄 Restoring session, registering FCM token...")
            try {
                fcmManager.getFCMTokenAndSendToServer(token)
                Log.d(TAG, "✅ FCM token registration initiated on app restart")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to register FCM token on restart", e)
                e.printStackTrace()
            }
        }
    }

    fun login(
        accessToken: String,
        expiryTime: Long? = null,
        refreshToken: String? = null,
        refreshExpiryTime: Long? = null
    ) {
        Log.d(TAG, "🔐 Login function called")
        Log.d(TAG, "   Access token: ${accessToken.take(20)}...")
        
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true)
            authManager.saveAuthSession(
                accessToken = accessToken,
                accessTokenExpiryTime = expiryTime,
                refreshTokenValue = refreshToken,
                refreshTokenExpiryTime = refreshExpiryTime
            )
            val profile = authManager.getUserProfileOnce()
            Log.d(TAG, "👤 User profile loaded: ${profile?.email}")
            
            _authState.value = AuthState(
                isLoggedIn = true,
                accessToken = accessToken,
                isLoading = false,
                isInitialized = true,
                userId = profile?.id,
                userEmail = profile?.email,
                userFullName = profile?.fullName,
                userRole = profile?.role,
                userFriendCount = profile?.friendCount,
                userLocation = profile?.location,
                userHometown = profile?.hometown,
                userBirthYear = profile?.birthYear
            )
            
            Log.d(TAG, "🔔 Starting FCM token registration process...")
            
            // Send FCM token to backend after successful login
            try {
                fcmManager.getFCMTokenAndSendToServer(accessToken)
                Log.d(TAG, "✅ FCM token registration initiated")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to register FCM token", e)
                e.printStackTrace()
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            // Deactivate FCM token before logout
            try {
                val token = authManager.getValidAccessToken()
                if (token != null) {
                    fcmManager.deactivateToken(token)
                    Log.d(TAG, "✅ FCM token deactivated")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to deactivate FCM token", e)
            }
            
            authManager.logout()
            _authState.value = AuthState(isLoggedIn = false, accessToken = null, isInitialized = true)
        }
    }
    
    /**
     * Get FCM Manager instance for permission requests
     */
    fun getFCMManager() = fcmManager

    fun refreshToken() {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true)
            val result = repository.refreshAccessToken()
            if (result.isSuccess) {
                val newToken = result.getOrNull()
                val profile = authManager.getUserProfileOnce()
                _authState.value = _authState.value.copy(
                    isLoggedIn = newToken != null,
                    accessToken = newToken,
                    isLoading = false,
                    userId = profile?.id,
                    userEmail = profile?.email,
                    userFullName = profile?.fullName,
                    userRole = profile?.role,
                    userFriendCount = profile?.friendCount,
                    userLocation = profile?.location,
                    userHometown = profile?.hometown,
                    userBirthYear = profile?.birthYear
                )
            } else {
                logout()
            }
        }
    }

    // Network-based auth
    suspend fun loginWithNetwork(username: String, password: String): Result<Unit> {
        _authState.value = _authState.value.copy(isLoading = true)
        return try {
            val result = repository.login(username, password)
            if (result.isSuccess) {
                val response = result.getOrNull()
                val token = response?.accessToken ?: authManager.getValidAccessToken()
                val profile = authManager.getUserProfileOnce()
                _authState.value = AuthState(
                    isLoggedIn = true,
                    accessToken = token,
                    isLoading = false,
                    isInitialized = true,
                    userId = profile?.id ?: _authState.value.userId,
                    userEmail = profile?.email ?: _authState.value.userEmail,
                    userFullName = profile?.fullName ?: _authState.value.userFullName,
                    userRole = profile?.role ?: _authState.value.userRole,
                    userFriendCount = profile?.friendCount ?: _authState.value.userFriendCount,
                    userLocation = profile?.location ?: _authState.value.userLocation,
                    userHometown = profile?.hometown ?: _authState.value.userHometown,
                    userBirthYear = profile?.birthYear ?: _authState.value.userBirthYear
                )
                
                // ✅ FIX: Register FCM token after successful login
                Log.d(TAG, "🔔 Starting FCM token registration process...")
                try {
                    token?.let {
                        fcmManager.getFCMTokenAndSendToServer(it)
                        Log.d(TAG, "✅ FCM token registration initiated")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to register FCM token", e)
                    e.printStackTrace()
                }
                
                Result.success(Unit)
            } else {
                _authState.value = _authState.value.copy(isLoading = false)
                Result.failure(result.exceptionOrNull() ?: Exception("Login failed"))
            }
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(isLoading = false)
            Result.failure(e)
        }
    }

    suspend fun registerAccount(email: String, password: String, fullName: String): Result<Unit> {
        _authState.value = _authState.value.copy(isLoading = true)
        return try {
            val result = repository.register(email, password, fullName)
            _authState.value = _authState.value.copy(isLoading = false)
            result
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(isLoading = false)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        fullName: String? = null,
        location: String? = null,
        hometown: String? = null,
        birthYear: Int? = null
    ): Result<Unit> {
        _authState.value = _authState.value.copy(isLoading = true)
        return try {
            val result = repository.updateProfile(fullName, location, hometown, birthYear)
            if (result.isSuccess) {
                val user = result.getOrNull()
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    userFullName = user?.fullName ?: _authState.value.userFullName,
                    userLocation = user?.location ?: _authState.value.userLocation,
                    userHometown = user?.hometown ?: _authState.value.userHometown,
                    userBirthYear = user?.birthYear ?: _authState.value.userBirthYear,
                    userFriendCount = user?.friendCount ?: _authState.value.userFriendCount
                )
                Result.success(Unit)
            } else {
                _authState.value = _authState.value.copy(isLoading = false)
                Result.failure(result.exceptionOrNull() ?: Exception("Update failed"))
            }
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(isLoading = false)
            Result.failure(e)
        }
    }

    suspend fun changePassword(
        oldPassword: String,
        newPassword: String
    ): Result<Unit> {
        return repository.changePassword(oldPassword, newPassword)
    }
}

