package com.example.chatapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.local.AuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthState(
    val isLoggedIn: Boolean = false,
    val accessToken: String? = null,
    val isLoading: Boolean = false,
    val isInitialized: Boolean = false
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val authManager = AuthManager(application)

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
            isInitialized = true
        )
    }

    fun login(accessToken: String, expiryTime: Long? = null) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true)
            authManager.saveAccessToken(accessToken, expiryTime)
            _authState.value = AuthState(
                isLoggedIn = true,
                accessToken = accessToken,
                isLoading = false,
                isInitialized = true
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

    // Login functions
    fun performLogin(username: String, password: String): Boolean {
        // Fixed credentials check
        return username == "admin" && password == "123456"
    }
}

