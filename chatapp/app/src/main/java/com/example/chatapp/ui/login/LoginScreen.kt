package com.example.chatapp.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.MainActivity
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
    val context = LocalContext.current

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            // Request notification permission after successful login
            val fcmManager = authViewModel.getFCMManager()
            if (!fcmManager.hasNotificationPermission()) {
                (context as? MainActivity)?.let { activity ->
                    fcmManager.requestNotificationPermission(activity)
                }
            }
            onLoginSuccess()
        }
    }

    KeyboardDismissWrapper(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF667EEA),
                            Color(0xFF764BA2)
                        )
                    )
                )
        ) {
            // Decorative circles
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .offset(x = (-50).dp, y = (-50).dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(100.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 50.dp, y = (-30).dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(100.dp)
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo/App Name Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "💬",
                            fontSize = 48.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "ChatApp",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                    Text(
                        text = if (state.isRegisterMode) "Tạo tài khoản mới" else "Chào mừng trở lại",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 16.sp
                    )
                }

                // Login Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title
                        Text(
                            text = if (state.isRegisterMode) "Đăng ký" else "Đăng nhập",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Email Field
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = loginViewModel::onUsernameChange,
                            label = { Text("Email") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Email,
                                    contentDescription = "Email",
                                    tint = Color(0xFF667EEA)
                                )
                            },
                            singleLine = true,
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF667EEA),
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedLabelColor = Color(0xFF667EEA)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Full Name Field (Register Mode)
                        AnimatedVisibility(
                            visible = state.isRegisterMode,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            OutlinedTextField(
                                value = state.fullName,
                                onValueChange = loginViewModel::onFullNameChange,
                                label = { Text("Họ và tên") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = "Họ và tên",
                                        tint = Color(0xFF667EEA)
                                    )
                                },
                                singleLine = true,
                                enabled = !state.isLoading,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF667EEA),
                                    unfocusedBorderColor = Color(0xFFE0E0E0),
                                    focusedLabelColor = Color(0xFF667EEA)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Password Field
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = loginViewModel::onPasswordChange,
                            label = { Text("Mật khẩu") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Mật khẩu",
                                    tint = Color(0xFF667EEA)
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF667EEA),
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedLabelColor = Color(0xFF667EEA)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Error Message
                        AnimatedVisibility(
                            visible = state.errorMessage != null,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFEBEE)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = state.errorMessage ?: "",
                                    color = Color(0xFFC62828),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Forgot Password (Login Mode)
                        AnimatedVisibility(
                            visible = !state.isRegisterMode,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            TextButton(
                                onClick = onForgotPassword,
                                enabled = !state.isLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Quên mật khẩu?",
                                    color = Color(0xFF667EEA),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Login/Register Button
                        Button(
                            onClick = {
                                if (state.isRegisterMode) loginViewModel.register() else loginViewModel.login()
                            },
                            enabled = !state.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF667EEA),
                                disabledContainerColor = Color(0xFFBDBDBD)
                            )
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    if (state.isRegisterMode) "Đăng ký" else "Đăng nhập",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Toggle Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (state.isRegisterMode) "Đã có tài khoản? " else "Chưa có tài khoản? ",
                                color = Color(0xFF757575),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(
                                onClick = { loginViewModel.toggleMode() },
                                enabled = !state.isLoading
                            ) {
                                Text(
                                    if (state.isRegisterMode) "Đăng nhập" else "Đăng ký",
                                    color = Color(0xFF667EEA),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


