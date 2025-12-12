package com.example.chatapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.model.NotificationDto
import com.example.chatapp.utils.formatTimeAgo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    navController: androidx.navigation.NavController? = null,
    chatViewModel: com.example.chatapp.viewmodel.ChatViewModel? = null,
    onUnreadCountChange: (Int) -> Unit = {}
) {
    var notifications by remember { mutableStateOf<List<NotificationDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun loadNotifications() {
        scope.launch {
            try {
                isLoading = true
                val auth = AuthManager(context)
                val token = auth.getValidAccessToken() ?: return@launch
                // Chỉ lấy 6 thông báo
                val response = ApiClient.apiService.getNotifications("Bearer $token", limit = 6)
                notifications = response.items
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
                e.printStackTrace()
            }
        }
    }

    fun loadUnreadCount() {
        scope.launch {
            try {
                val auth = AuthManager(context)
                val token = auth.getValidAccessToken() ?: return@launch
                val response = ApiClient.apiService.getUnreadNotificationCount("Bearer $token")
                onUnreadCountChange(response.count)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun markAllAsRead() {
        scope.launch {
            try {
                val auth = AuthManager(context)
                val token = auth.getValidAccessToken() ?: return@launch
                ApiClient.apiService.markAllNotificationsRead("Bearer $token")
                // Update local state
                notifications = notifications.map { it.copy(isRead = true) }
                // Refresh unread count
                loadUnreadCount()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        scope.launch {
            try {
                val auth = AuthManager(context)
                val token = auth.getValidAccessToken() ?: return@launch
                ApiClient.apiService.deleteNotification("Bearer $token", notificationId)
                // Remove from local list
                notifications = notifications.filter { it.id != notificationId }
                // Refresh unread count
                loadUnreadCount()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Khi vào screen thì tự động đánh dấu tất cả đã đọc
    LaunchedEffect(Unit) {
        loadNotifications()
        markAllAsRead()
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF667EEA),
                                Color(0xFF764BA2)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Thông báo",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
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
        } else if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔔", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Chưa có thông báo nào",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF757575)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.White),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(notifications) { notification ->
                    NotificationItem(
                        notification = notification,
                        onDelete = { deleteNotification(notification.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: NotificationDto,
    onDelete: () -> Unit = {}
) {
    val iconColor = when (notification.type) {
        "friend_request" -> Color(0xFF2196F3)
        "friend_accept" -> Color(0xFF4CAF50)
        "friend_decline", "friend_cancel" -> Color(0xFFFF9800)
        "unfriend" -> Color(0xFFF44336)
        else -> Color(0xFF757575)
    }
    val icon = when (notification.type) {
        "friend_request" -> Icons.Default.PersonAdd
        "friend_accept" -> Icons.Default.CheckCircle
        "friend_decline", "friend_cancel" -> Icons.Default.Cancel
        "unfriend" -> Icons.Default.PersonRemove
        else -> Icons.Default.Info
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF757575)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimeAgo(notification.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9E9E9E)
                )
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Xóa",
                    tint = Color(0xFF9E9E9E),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Divider()
    }
}

