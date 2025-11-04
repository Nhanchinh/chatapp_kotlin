package com.example.chatapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val fullName: String = "",
    val isRegisterMode: Boolean = false,
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

    fun onFullNameChange(value: String) {
        _uiState.value = _uiState.value.copy(fullName = value, errorMessage = null)
    }

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            isRegisterMode = !_uiState.value.isRegisterMode,
            errorMessage = null
        )
    }

    fun login() {
        val current = _uiState.value
        if (current.username.isBlank() || current.password.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Vui lòng nhập đầy đủ thông tin")
            return
        }
        _uiState.value = current.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authViewModel.loginWithNetwork(current.username, current.password)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
            } else {
                val message = result.exceptionOrNull()?.localizedMessage ?: "Đăng nhập thất bại"
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = message)
            }
        }
    }

    fun register() {
        val current = _uiState.value
        if (current.username.isBlank() || current.password.isBlank() || current.fullName.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Vui lòng nhập đầy đủ thông tin")
            return
        }
        _uiState.value = current.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authViewModel.registerAccount(
                email = current.username,
                password = current.password,
                fullName = current.fullName
            )
            if (result.isSuccess) {
                // Auto login after successful register
                val loginResult = authViewModel.loginWithNetwork(current.username, current.password)
                if (loginResult.isSuccess) {
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
                } else {
                    val message = loginResult.exceptionOrNull()?.localizedMessage ?: "Đăng nhập sau đăng ký thất bại"
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = message)
                }
            } else {
                val message = result.exceptionOrNull()?.localizedMessage ?: "Đăng ký thất bại"
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = message)
            }
        }
    }
}


