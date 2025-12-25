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
    // Validation states
    val emailError: String? = null,
    val passwordStrength: PasswordStrength = PasswordStrength.NONE,
    val passwordError: String? = null,
    // OTP states
    val showOtpDialog: Boolean = false,
    val otpValue: String = "",
    val otpExpiresIn: Int = 0,
    val otpMessage: String? = null
)

enum class PasswordStrength {
    NONE,       // Empty
    WEAK,       // < 6 chars or only letters/numbers
    MEDIUM,     // 6+ chars with letters and numbers
    STRONG      // 8+ chars with letters, numbers, and special chars
}

class LoginViewModel(
    private val authViewModel: AuthViewModel
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    // Email regex pattern
    private val emailPattern = android.util.Patterns.EMAIL_ADDRESS
    
    // Validate email format
    private fun validateEmail(email: String): String? {
        return when {
            email.isBlank() -> null  // Don't show error for empty field
            !emailPattern.matcher(email).matches() -> "Email không đúng định dạng"
            else -> null
        }
    }
    
    // Calculate password strength
    private fun calculatePasswordStrength(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.NONE
        
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        
        return when {
            password.length >= 8 && hasLetter && hasDigit && hasSpecial -> PasswordStrength.STRONG
            password.length >= 6 && hasLetter && hasDigit -> PasswordStrength.MEDIUM
            password.length >= 6 -> PasswordStrength.WEAK
            else -> PasswordStrength.WEAK
        }
    }

    fun onUsernameChange(value: String) {
        val emailError = validateEmail(value)
        _uiState.value = _uiState.value.copy(
            username = value, 
            emailError = emailError,
            errorMessage = null
        )
    }

    fun onPasswordChange(value: String) {
        val strength = calculatePasswordStrength(value)
        val passwordError = if (_uiState.value.isRegisterMode && value.length in 1..5) {
            "Mật khẩu phải có ít nhất 6 ký tự"
        } else null
        
        _uiState.value = _uiState.value.copy(
            password = value, 
            passwordStrength = strength,
            passwordError = passwordError,
            errorMessage = null
        )
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
