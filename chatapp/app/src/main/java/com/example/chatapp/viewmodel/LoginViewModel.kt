package com.example.chatapp.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false
)

class LoginViewModel(
    private val authViewModel: AuthViewModel
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun login() {
        val current = _uiState.value
        if (current.username.isBlank() || current.password.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Vui lòng nhập đầy đủ thông tin")
            return
        }
        _uiState.value = current.copy(isLoading = true, errorMessage = null)

        // Fixed credentials check
        val isValid = current.username == "admin" && current.password == "123456"
        
        if (isValid) {
            // Save token and login state
            // Generate a fake access token (fixed for now)
            val fakeAccessToken = "fake_access_token_${System.currentTimeMillis()}"
            // Token expiry set to 7 days from now
            val expiryTime = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000)
            
            authViewModel.login(fakeAccessToken, expiryTime)
            _uiState.value = current.copy(isLoading = false, isLoggedIn = true)
        } else {
            _uiState.value = current.copy(isLoading = false, errorMessage = "Sai tài khoản hoặc mật khẩu")
        }
    }
}


