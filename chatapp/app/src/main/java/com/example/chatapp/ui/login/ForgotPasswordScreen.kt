package com.example.chatapp.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chatapp.ui.common.KeyboardDismissWrapper
import com.example.chatapp.viewmodel.ForgotPasswordStep
import com.example.chatapp.viewmodel.ForgotPasswordViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quên mật khẩu") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        KeyboardDismissWrapper(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
            when (state.step) {
                ForgotPasswordStep.ENTER_EMAIL -> {
                    Text(
                        text = "Nhập email đã đăng ký để nhận mã OTP đặt lại mật khẩu.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = viewModel::updateEmail,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !state.isLoading
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ActionMessage(state.message, state.errorMessage)
                    Button(
                        onClick = viewModel::submitEmail,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        } else {
                            Text("Gửi OTP")
                        }
                    }
                }

                ForgotPasswordStep.VERIFY_OTP -> {
                    Text(
                        text = "Nhập mã OTP đã được gửi tới email ${state.email}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.otp,
                        onValueChange = viewModel::updateOtp,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Mã OTP") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !state.isLoading
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ActionMessage(state.message, state.errorMessage)
                    Button(
                        onClick = viewModel::submitOtp,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        } else {
                            Text("Xác nhận OTP")
                        }
                    }
                    TextButton(
                        onClick = { viewModel.resendOtp() },
                        enabled = !state.isLoading
                    ) {
                        Text("Gửi lại OTP")
                    }
                }

                ForgotPasswordStep.RESET_PASSWORD -> {
                    Text(
                        text = "Tạo mật khẩu mới cho tài khoản ${state.email}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.newPassword,
                        onValueChange = viewModel::updateNewPassword,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Mật khẩu mới") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !state.isLoading
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.confirmPassword,
                        onValueChange = viewModel::updateConfirmPassword,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Xác nhận mật khẩu") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !state.isLoading
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ActionMessage(state.message, state.errorMessage)
                    Button(
                        onClick = viewModel::submitNewPassword,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        } else {
                            Text("Đặt lại mật khẩu")
                        }
                    }
                }

                ForgotPasswordStep.SUCCESS -> {
                    Text(
                        text = state.message ?: "Bạn đã đổi mật khẩu thành công",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onFinished,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Quay lại đăng nhập")
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ActionMessage(message: String?, error: String?) {
    when {
        !error.isNullOrBlank() -> {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        !message.isNullOrBlank() -> {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

