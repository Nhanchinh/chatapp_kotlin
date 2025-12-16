package com.example.chatapp.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import com.example.chatapp.viewmodel.AuthViewModel
import com.example.chatapp.ui.common.KeyboardDismissWrapper
import com.example.chatapp.data.local.SettingsManager
import com.example.chatapp.data.local.AuthManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToBackup: () -> Unit = {},
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val authManager = remember { AuthManager(context) }
    val fcmManager = authViewModel.getFCMManager()
    
    var notificationsEnabled by remember { mutableStateOf(true) }
    
    // Load current notification state - check if user has active FCM tokens
    LaunchedEffect(Unit) {
        try {
            val token = authManager.getValidAccessToken()
            if (token != null) {
                val savedToken = fcmManager.getSavedFCMToken()
                notificationsEnabled = savedToken != null && fcmManager.isTokenSentToServer()
            }
        } catch (e: Exception) {
            notificationsEnabled = false
        }
    }
    
    // E2EE setting - collect from Flow
    var e2eeEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        settingsManager.isE2EEEnabled.collect { enabled ->
            e2eeEnabled = enabled
        }
    }
    
    var showChangePassword by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var changeError by remember { mutableStateOf<String?>(null) }
    var changeMessage by remember { mutableStateOf<String?>(null) }
    var changeLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                    .verticalScroll(rememberScrollState())
                    .background(Color.White)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Thông báo section
                Text(
                    text = "Thông báo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = Color.Gray
                )
                
                // Bật thông báo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Thông báo",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Nhận thông báo tin nhắn mới",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            notificationsEnabled = enabled
                            scope.launch {
                                try {
                                    val token = authManager.getValidAccessToken()
                                    if (token != null) {
                                        if (enabled) {
                                            // Bật: Register FCM token
                                            fcmManager.getFCMTokenAndSendToServer(token)
                                        } else {
                                            // Tắt: Deactivate FCM token
                                            val fcmToken = fcmManager.getSavedFCMToken()
                                            if (fcmToken != null) {
                                                fcmManager.deactivateToken(token)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    // Revert on error
                                    notificationsEnabled = !enabled
                                }
                            }
                        }
                    )
                }
                
                Divider(modifier = Modifier.padding(top = 8.dp))
                
                // Tài khoản section
                Text(
                    text = "Tài khoản",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = Color.Gray
                )
                
                // Thông tin cá nhân
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Thông tin cá nhân",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
                
                Divider()
                
                // Bảo mật section
                Text(
                    text = "Bảo mật",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = Color.Gray
                )
                
                // E2EE Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mã hóa đầu cuối (E2EE)",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (e2eeEnabled) "Tin nhắn được mã hóa" else "Tin nhắn không được mã hóa",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = e2eeEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                settingsManager.setE2EEEnabled(enabled)
                            }
                        }
                    )
                }
                
                // Sao lưu tin nhắn
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToBackup() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sao lưu tin nhắn",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Sao lưu khóa để khôi phục tin nhắn",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
                
                // Đổi mật khẩu - Collapsible
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showChangePassword = !showChangePassword }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Đổi mật khẩu",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (showChangePassword) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
                
                // Form đổi mật khẩu - Animated visibility
                AnimatedVisibility(
                    visible = showChangePassword,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = oldPassword,
                            onValueChange = {
                                oldPassword = it
                                changeError = null
                                changeMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Mật khẩu hiện tại") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !changeLoading
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = {
                                newPassword = it
                                changeError = null
                                changeMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Mật khẩu mới") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !changeLoading
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                changeError = null
                                changeMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Xác nhận mật khẩu mới") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !changeLoading
                        )
                        if (!changeError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = changeError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!changeMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = changeMessage ?: "",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (newPassword != confirmPassword) {
                                    changeError = "Mật khẩu xác nhận không khớp"
                                    return@Button
                                }
                                if (newPassword.length < 6) {
                                    changeError = "Mật khẩu mới phải có ít nhất 6 ký tự"
                                    return@Button
                                }
                                scope.launch {
                                    changeLoading = true
                                    changeError = null
                                    changeMessage = null
                                    val result = authViewModel.changePassword(oldPassword, newPassword)
                                    changeLoading = false
                                    if (result.isSuccess) {
                                        changeMessage = "Đổi mật khẩu thành công"
                                        oldPassword = ""
                                        newPassword = ""
                                        confirmPassword = ""
                                    } else {
                                        changeError =
                                            result.exceptionOrNull()?.message ?: "Không thể đổi mật khẩu"
                                    }
                                }
                            },
                            enabled = !changeLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (changeLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(end = 8.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            }
                            Text("Đổi mật khẩu")
                        }
                    }
                }
                
                Divider(modifier = Modifier.padding(top = 8.dp))
                
                // Giúp đỡ section
                Text(
                    text = "Giúp đỡ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = Color.Gray
                )
                
                // Trợ giúp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Help,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Trợ giúp",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
                
                Divider()
                
                // Giới thiệu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Giới thiệu",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "1.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}
