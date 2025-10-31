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

class LoginViewModel : ViewModel() {
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
        _uiState.value = if (isValid) {
            _uiState.value.copy(isLoading = false, isLoggedIn = true)
        } else {
            _uiState.value.copy(isLoading = false, errorMessage = "Sai tài khoản hoặc mật khẩu")
        }
    }
}


