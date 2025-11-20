package com.example.chatapp.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.chatapp.viewmodel.AuthViewModel
import com.example.chatapp.viewmodel.LoginViewModel
import com.example.chatapp.ui.common.KeyboardDismissWrapper

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    authViewModel: AuthViewModel,
    onForgotPassword: () -> Unit = {}
) {
    val loginViewModel = remember { LoginViewModel(authViewModel) }
    val state by loginViewModel.uiState.collectAsState()

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoginSuccess()
    }

    KeyboardDismissWrapper(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Text(text = if (state.isRegisterMode) "Đăng ký" else "Đăng nhập", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = state.username,
            onValueChange = loginViewModel::onUsernameChange,
            label = { Text("Email") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (state.isRegisterMode) {
            OutlinedTextField(
                value = state.fullName,
                onValueChange = loginViewModel::onFullNameChange,
                label = { Text("Họ và tên") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = state.password,
            onValueChange = loginViewModel::onPasswordChange,
            label = { Text("Mật khẩu") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        if (state.errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = state.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
        }

        if (!state.isRegisterMode) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onForgotPassword,
                enabled = !state.isLoading
            ) {
                Text("Quên mật khẩu?")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (state.isRegisterMode) loginViewModel.register() else loginViewModel.login()
            },
            enabled = !state.isLoading
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            } else {
                Text(if (state.isRegisterMode) "Đăng ký" else "Đăng nhập")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { loginViewModel.toggleMode() }, enabled = !state.isLoading) {
            Text(if (state.isRegisterMode) "Đã có tài khoản? Đăng nhập" else "Chưa có tài khoản? Đăng ký")
        }
        }
    }
}


