package com.example.chatapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.model.UserDto
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.chatapp.utils.UrlHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherUserProfileScreen(
    userId: String,
    onBack: () -> Unit
) {
    var userInfo by remember { mutableStateOf<UserDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Blue gradient color for avatar border (matches app theme)
    val blueGradient = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(Color(0xFF64B5F6), Color(0xFF2196F3))
    )

    LaunchedEffect(userId) {
        isLoading = true
        error = null
        try {
            val auth = AuthManager(context)
            val token = auth.getValidAccessToken()
            if (token != null) {
                val user = ApiClient.apiService.getUserById("Bearer $token", userId)
                userInfo = user
            } else {
                error = "Không thể xác thực"
            }
        } catch (e: Exception) {
            error = "Không thể tải thông tin: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black
                        )
                    }
                },
                title = {
                    Text(
                        text = "Trang cá nhân",
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF2196F3)
                )
            } else if (error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = error ?: "Đã xảy ra lỗi",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Text("Quay lại")
                    }
                }
            } else {
                val currentUserInfo = userInfo
                if (currentUserInfo != null) {
                    val userName = currentUserInfo.fullName ?: currentUserInfo.email ?: "Unknown"
                    val userEmail = currentUserInfo.email ?: ""
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Profile header: Avatar left, Info right (same as UserProfileScreen)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar with blue gradient border
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(blueGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(82.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val avatarUrl = UrlHelper.avatar(currentUserInfo.avatar)
                                    if (avatarUrl != null) {
                                        AsyncImage(
                                            model = avatarUrl,
                                            contentDescription = "Profile photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(78.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(78.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFB39DDB)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = userName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase(),
                                                color = Color.White,
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            // User info
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                // Name
                                Text(
                                    text = userName,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                
                                // Email
                                if (userEmail.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = userEmail,
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                                
                                // Friend count badge
                                currentUserInfo.friendCount?.let { count ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = Color(0xFFE3F2FD),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Group,
                                                contentDescription = null,
                                                tint = Color(0xFF2196F3),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "$count người bạn",
                                                fontSize = 13.sp,
                                                color = Color(0xFF2196F3),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Chi tiết section (same as UserProfileScreen but without edit button)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "Chi tiết",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            OtherProfileDetailItem(
                                icon = Icons.Default.Home,
                                label = "Sống tại",
                                value = currentUserInfo.location ?: "Chưa cập nhật"
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OtherProfileDetailItem(
                                icon = Icons.Default.LocationCity,
                                label = "Đến từ",
                                value = currentUserInfo.hometown ?: "Chưa cập nhật"
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OtherProfileDetailItem(
                                icon = Icons.Default.Cake,
                                label = "Năm sinh",
                                value = currentUserInfo.birthYear?.toString() ?: "Chưa cập nhật"
                            )
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun OtherProfileDetailItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF5F5F5),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }
        }
    }
}
