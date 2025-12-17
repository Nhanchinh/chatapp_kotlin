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
    val isLoggedIn: Boolean = false,
    // OTP states
    val showOtpDialog: Boolean = false,
    val otpValue: String = "",
    val otpExpiresIn: Int = 0,
    val otpMessage: String? = null
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

    fun onOtpChange(value: String) {
        if (value.length <= 6 && value.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(otpValue = value, errorMessage = null)
        }
    }

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            isRegisterMode = !_uiState.value.isRegisterMode,
            errorMessage = null
        )
    }

    fun dismissOtpDialog() {
        _uiState.value = _uiState.value.copy(
            showOtpDialog = false,
            otpValue = "",
            otpMessage = null,
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

    /**
     * Step 1: Request OTP for registration
     */
    fun register() {
        val current = _uiState.value
        if (current.username.isBlank() || current.password.isBlank() || current.fullName.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Vui lòng nhập đầy đủ thông tin")
            return
        }
        _uiState.value = current.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authViewModel.requestRegistrationOtp(
                email = current.username,
                password = current.password,
                fullName = current.fullName
            )
            if (result.isSuccess) {
                val response = result.getOrNull()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showOtpDialog = true,
                    otpExpiresIn = response?.expiresIn ?: 120,
                    otpMessage = response?.message ?: "OTP đã được gửi đến email của bạn"
                )
            } else {
                val message = result.exceptionOrNull()?.localizedMessage ?: "Không thể gửi OTP"
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = message)
            }
        }
    }

    /**
     * Step 2: Verify OTP and complete registration
     */
    fun verifyRegistrationOtp() {
        val current = _uiState.value
        if (current.otpValue.length != 6) {
            _uiState.value = current.copy(errorMessage = "Vui lòng nhập đủ 6 số OTP")
            return
        }
        _uiState.value = current.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authViewModel.verifyRegistrationOtp(
                email = current.username,
                otp = current.otpValue
            )
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showOtpDialog = false,
                    isLoggedIn = true
                )
            } else {
                val message = result.exceptionOrNull()?.localizedMessage ?: "Xác thực OTP thất bại"
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = message)
            }
        }
    }

    /**
     * Resend OTP for registration
     */
    fun resendRegistrationOtp() {
        val current = _uiState.value
        _uiState.value = current.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authViewModel.requestRegistrationOtp(
                email = current.username,
                password = current.password,
                fullName = current.fullName
            )
            if (result.isSuccess) {
                val response = result.getOrNull()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    otpValue = "",
                    otpExpiresIn = response?.expiresIn ?: 120,
                    otpMessage = "OTP mới đã được gửi!"
                )
            } else {
                val message = result.exceptionOrNull()?.localizedMessage ?: "Không thể gửi lại OTP"
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = message)
            }
        }
    }
}
