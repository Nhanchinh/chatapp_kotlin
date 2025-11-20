package com.example.chatapp.viewmodel

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ForgotPasswordStep {
    ENTER_EMAIL,
    VERIFY_OTP,
    RESET_PASSWORD,
    SUCCESS
}

data class ForgotPasswordUiState(
    val step: ForgotPasswordStep = ForgotPasswordStep.ENTER_EMAIL,
    val email: String = "",
    val otp: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val resetToken: String? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(application)

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun updateEmail(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null, message = null) }
    }

    fun updateOtp(value: String) {
        _uiState.update { it.copy(otp = value, errorMessage = null, message = null) }
    }

    fun updateNewPassword(value: String) {
        _uiState.update { it.copy(newPassword = value, errorMessage = null, message = null) }
    }

    fun updateConfirmPassword(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null, message = null) }
    }

    fun submitEmail() {
        val email = uiState.value.email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(errorMessage = "Email không hợp lệ") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, message = null) }
            val result = repository.requestPasswordOtp(email)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        step = ForgotPasswordStep.VERIFY_OTP,
                        message = result.getOrNull(),
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Không thể gửi OTP"
                    )
                }
            }
        }
    }

    fun resendOtp() {
        val email = uiState.value.email.trim()
        if (email.isBlank()) return
        submitEmail()
    }

    fun submitOtp() {
        val email = uiState.value.email.trim()
        val otpValue = uiState.value.otp.trim()
        if (otpValue.length < 4) {
            _uiState.update { it.copy(errorMessage = "OTP không hợp lệ") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, message = null) }
            val result = repository.verifyPasswordOtp(email, otpValue)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        resetToken = result.getOrNull(),
                        step = ForgotPasswordStep.RESET_PASSWORD,
                        message = "OTP chính xác, hãy tạo mật khẩu mới"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Không thể xác minh OTP"
                    )
                }
            }
        }
    }

    fun submitNewPassword() {
        val email = uiState.value.email.trim()
        val newPassword = uiState.value.newPassword
        val confirm = uiState.value.confirmPassword
        val token = uiState.value.resetToken

        if (newPassword.length < 6) {
            _uiState.update { it.copy(errorMessage = "Mật khẩu phải có ít nhất 6 ký tự") }
            return
        }
        if (newPassword != confirm) {
            _uiState.update { it.copy(errorMessage = "Mật khẩu xác nhận không khớp") }
            return
        }
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Thiếu thông tin xác thực") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, message = null) }
            val result = repository.resetPassword(email, newPassword, token)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        step = ForgotPasswordStep.SUCCESS,
                        message = "Đổi mật khẩu thành công! Vui lòng đăng nhập lại."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Không thể đặt lại mật khẩu"
                    )
                }
            }
        }
    }

    fun resetFlow() {
        _uiState.value = ForgotPasswordUiState()
    }
}


