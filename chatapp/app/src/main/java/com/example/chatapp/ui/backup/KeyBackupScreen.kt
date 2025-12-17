package com.example.chatapp.ui.backup

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.chatapp.data.encryption.KeyBackupManager
import com.example.chatapp.data.encryption.KeyManager
import com.example.chatapp.data.local.AuthManager
import kotlinx.coroutines.launch

/**
 * Screen for managing message key backup/restore
 * Located in Settings -> "Sao lưu tin nhắn"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyBackupScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyBackupManager = remember { KeyBackupManager(context) }
    val keyManager = remember { KeyManager(context) }
    val authManager = remember { AuthManager(context) }
    
    // State
    var isLoading by remember { mutableStateOf(true) }
    var hasBackup by remember { mutableStateOf(false) }
    var backupConversationCount by remember { mutableStateOf(0) }
    var localConversationIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var backupUpdatedAt by remember { mutableStateOf<String?>(null) }
    
    // PIN dialogs
    var showCreateBackupDialog by remember { mutableStateOf(false) }
    var showRestoreBackupDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    
    // Error/Success messages
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    
    // Load initial data
    LaunchedEffect(Unit) {
        try {
            val token = authManager.getValidAccessToken()
            if (token != null) {
                // Check if backup exists on server
                hasBackup = keyBackupManager.hasBackup(token)
                
                if (hasBackup) {
                    val info = keyBackupManager.getBackupInfo(token)
                    if (info != null) {
                        backupConversationCount = info.first
                        backupUpdatedAt = info.second
                    }
                }
                
                // **AUTO-CLEANUP**: Remove orphan keys for deleted conversations
                val cleanedUp = keyBackupManager.cleanupOrphanKeys(token)
                if (cleanedUp > 0) {
                    android.util.Log.d("KeyBackupScreen", "Cleaned up $cleanedUp orphan keys")
                }
                
                // Get local conversation IDs (after cleanup)
                localConversationIds = keyManager.getBackedUpConversationIds()
            }
        } catch (e: Exception) {
            message = "Lỗi tải thông tin: ${e.message}"
            isError = true
        } finally {
            isLoading = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sao lưu tin nhắn") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                // Info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Sao lưu khóa mã hóa",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Lưu trữ khóa để khôi phục tin nhắn khi đổi thiết bị",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Current status
                Text(
                    text = "Trạng thái",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasBackup) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (hasBackup) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (hasBackup) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasBackup) "Đã sao lưu" else "Chưa sao lưu",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            if (hasBackup && backupUpdatedAt != null) {
                                Text(
                                    text = "$backupConversationCount cuộc hội thoại",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            } else if (!hasBackup) {
                                Text(
                                    text = "Bạn có ${localConversationIds.size} cuộc hội thoại có thể sao lưu",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Text(
                    text = "Hành động",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Create/Update backup button
                Button(
                    onClick = { showCreateBackupDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (hasBackup) "Cập nhật sao lưu" else "Tạo sao lưu mới")
                }
                
                if (hasBackup) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Restore backup button
                    OutlinedButton(
                        onClick = { showRestoreBackupDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Khôi phục từ sao lưu")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Delete backup button
                    TextButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Xóa sao lưu")
                    }
                }
                
                // Message display
                message?.let { msg ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isError) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) Color.Red else Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Local conversations list
                if (localConversationIds.isNotEmpty()) {
                    Text(
                        text = "Cuộc hội thoại có khóa (${localConversationIds.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            localConversationIds.take(5).forEachIndexed { index, conversationId ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Chat,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = conversationId.take(20) + "...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            if (localConversationIds.size > 5) {
                                Text(
                                    text = "...và ${localConversationIds.size - 5} cuộc hội thoại khác",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Create Backup Dialog
        if (showCreateBackupDialog) {
            PinInputDialog(
                title = if (hasBackup) "Cập nhật sao lưu" else "Tạo sao lưu mới",
                description = "Nhập mã PIN 6 số để mã hóa sao lưu. Bạn sẽ cần mã PIN này để khôi phục.",
                confirmButtonText = if (hasBackup) "Cập nhật" else "Tạo sao lưu",
                onDismiss = { showCreateBackupDialog = false },
                onConfirm = { pin ->
                    showCreateBackupDialog = false
                    scope.launch {
                        isLoading = true
                        try {
                            val token = authManager.getValidAccessToken()
                            if (token != null) {
                                val backup = keyBackupManager.createBackup(pin, token)
                                if (backup != null) {
                                    val success = keyBackupManager.uploadBackup(backup, token)
                                    if (success) {
                                        hasBackup = true
                                        backupConversationCount = backup.conversationIds.size
                                        message = "Sao lưu thành công ${backup.conversationIds.size} cuộc hội thoại"
                                        isError = false
                                    } else {
                                        message = "Không thể tải lên sao lưu"
                                        isError = true
                                    }
                                } else {
                                    message = "Không có dữ liệu để sao lưu"
                                    isError = true
                                }
                            }
                        } catch (e: Exception) {
                            message = "Lỗi: ${e.message}"
                            isError = true
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )
        }
        
        // Restore Backup Dialog
        if (showRestoreBackupDialog) {
            PinInputDialog(
                title = "Khôi phục sao lưu",
                description = "Nhập mã PIN đã dùng khi tạo sao lưu.",
                confirmButtonText = "Khôi phục",
                maxAttempts = KeyBackupManager.MAX_PIN_ATTEMPTS,
                onDismiss = { showRestoreBackupDialog = false },
                onConfirm = { pin ->
                    scope.launch {
                        isLoading = true
                        showRestoreBackupDialog = false
                        try {
                            val token = authManager.getValidAccessToken()
                            if (token != null) {
                                val (success, errorMsg) = keyBackupManager.restoreBackup(pin, token)
                                if (success) {
                                    localConversationIds = keyManager.getBackedUpConversationIds()
                                    message = "Khôi phục thành công ${localConversationIds.size} cuộc hội thoại"
                                    isError = false
                                } else {
                                    message = errorMsg ?: "Khôi phục thất bại"
                                    isError = true
                                }
                            }
                        } catch (e: Exception) {
                            message = "Lỗi: ${e.message}"
                            isError = true
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )
        }
        
        // Delete Confirm Dialog
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
                title = { Text("Xóa sao lưu?") },
                text = { Text("Bạn sẽ không thể khôi phục tin nhắn nếu đổi thiết bị. Hành động này không thể hoàn tác.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            scope.launch {
                                isLoading = true
                                try {
                                    val token = authManager.getValidAccessToken()
                                    if (token != null) {
                                        val success = keyBackupManager.deleteBackup(token)
                                        if (success) {
                                            hasBackup = false
                                            backupConversationCount = 0
                                            backupUpdatedAt = null
                                            message = "Đã xóa sao lưu"
                                            isError = false
                                        } else {
                                            message = "Không thể xóa sao lưu"
                                            isError = true
                                        }
                                    }
                                } catch (e: Exception) {
                                    message = "Lỗi: ${e.message}"
                                    isError = true
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text("Xóa")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Hủy")
                    }
                }
            )
        }
    }
}

/**
 * PIN Input Dialog for backup/restore operations
 */
@Composable
fun PinInputDialog(
    title: String,
    description: String,
    confirmButtonText: String,
    maxAttempts: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (pin: String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmStep by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(description, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = if (isConfirmStep) confirmPin else pin,
                    onValueChange = { value ->
                        if (value.length <= 6 && value.all { it.isDigit() }) {
                            if (isConfirmStep) confirmPin = value else pin = value
                            error = null
                        }
                    },
                    label = { Text(if (isConfirmStep) "Xác nhận mã PIN" else "Mã PIN (6 số)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (maxAttempts > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Còn ${maxAttempts - attempts} lần thử",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pin.length != 6) {
                        error = "Mã PIN phải có 6 số"
                        return@TextButton
                    }
                    
                    if (maxAttempts == 0 && !isConfirmStep) {
                        // For create backup: need to confirm PIN
                        isConfirmStep = true
                        return@TextButton
                    }
                    
                    if (maxAttempts == 0 && isConfirmStep) {
                        // For create backup: check if PINs match
                        if (pin != confirmPin) {
                            error = "Mã PIN không khớp"
                            confirmPin = ""
                            return@TextButton
                        }
                    }
                    
                    if (maxAttempts > 0) {
                        attempts++
                        if (attempts >= maxAttempts) {
                            onDismiss()
                            return@TextButton
                        }
                    }
                    
                    onConfirm(pin)
                },
                enabled = (if (isConfirmStep) confirmPin else pin).length == 6
            ) {
                Text(if (!isConfirmStep && maxAttempts == 0) "Tiếp tục" else confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
