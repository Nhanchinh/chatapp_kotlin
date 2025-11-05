package com.example.chatapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.repository.AuthRepository
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

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Load saved state on initialization
        viewModelScope.launch {
            loadAuthState()
        }
    }

    private suspend fun loadAuthState() {
        val isLoggedIn = authManager.isLoggedInOnce()
        val accessToken = if (isLoggedIn) authManager.getAccessTokenOnce() else null
        val user = authManager.getUserProfileOnce()
        
        // Check if token is expired
        val shouldExpire = if (isLoggedIn && authManager.isTokenExpired()) {
            handleTokenExpired()
            false // will be logged out
        } else {
            isLoggedIn
        }
        
        _authState.value = AuthState(
            isLoggedIn = shouldExpire,
            accessToken = if (shouldExpire) accessToken else null,
            isInitialized = true,
            userId = if (shouldExpire) user?.id else null,
            userEmail = if (shouldExpire) user?.email else null,
            userFullName = if (shouldExpire) user?.fullName else null,
            userRole = if (shouldExpire) user?.role else null,
            userFriendCount = if (shouldExpire) user?.friendCount else null,
            userLocation = if (shouldExpire) user?.location else null,
            userHometown = if (shouldExpire) user?.hometown else null,
            userBirthYear = if (shouldExpire) user?.birthYear else null
        )
    }

    fun login(accessToken: String, expiryTime: Long? = null) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true)
            authManager.saveAccessToken(accessToken, expiryTime)
            val profile = authManager.getUserProfileOnce()
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
        }
    }

    fun logout() {
        viewModelScope.launch {
            authManager.logout()
            _authState.value = AuthState(isLoggedIn = false, accessToken = null, isInitialized = true)
        }
    }

    private suspend fun handleTokenExpired() {
        // TODO: Call refresh token API here
        // For now, just logout if token is expired
        logout()
    }

    fun refreshToken() {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true)
            try {
                // TODO: Call refresh token API
                // val newToken = api.refreshToken()
                // authManager.saveAccessToken(newToken.token, newToken.expiryTime)
                
                // For now, just logout if refresh fails
                logout()
            } catch (e: Exception) {
                logout()
            } finally {
                _authState.value = _authState.value.copy(isLoading = false)
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
                val token = response?.accessToken ?: authManager.getAccessTokenOnce()
                val user = response?.user
                _authState.value = AuthState(
                    isLoggedIn = true,
                    accessToken = token,
                    isLoading = false,
                    isInitialized = true,
                    userId = user?.id ?: _authState.value.userId,
                    userEmail = user?.email ?: _authState.value.userEmail,
                    userFullName = user?.fullName ?: _authState.value.userFullName,
                    userRole = user?.role ?: _authState.value.userRole,
                    userFriendCount = user?.friendCount ?: _authState.value.userFriendCount,
                    userLocation = user?.location ?: _authState.value.userLocation,
                    userHometown = user?.hometown ?: _authState.value.userHometown,
                    userBirthYear = user?.birthYear ?: _authState.value.userBirthYear
                )
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
}

