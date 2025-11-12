package com.example.chatapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.model.UserDto

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

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
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
                    Button(onClick = onBack) {
                        Text("Quay lại")
                    }
                }
            } else {
                val currentUserInfo = userInfo
                if (currentUserInfo != null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        // Profile Header with gradient background
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF2196F3),
                                                Color(0xFF1976D2)
                                            )
                                        )
                                    )
                            ) {
                                // Back button at top
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Avatar with shadow
                                    Box(
                                        modifier = Modifier
                                            .size(110.dp)
                                            .shadow(
                                                elevation = 12.dp,
                                                shape = CircleShape,
                                                spotColor = Color.Black.copy(alpha = 0.3f)
                                            )
                                            .clip(CircleShape)
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(106.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    brush = Brush.radialGradient(
                                                        colors = listOf(
                                                            Color(0xFF90CAF9),
                                                            Color(0xFF64B5F6)
                                                        )
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = (currentUserInfo.fullName ?: currentUserInfo.email ?: "?").firstOrNull()?.uppercase() ?: "?",
                                                color = Color.White,
                                                style = MaterialTheme.typography.displayLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(20.dp))
                                    
                                    // Name
                                    Text(
                                        text = currentUserInfo.fullName ?: currentUserInfo.email ?: "Unknown",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    // Email
                                    currentUserInfo.email?.let { email ->
                                        Text(
                                            text = email,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White.copy(alpha = 0.95f)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Info Cards Section
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Friend count
                                currentUserInfo.friendCount?.let { friendCount ->
                                    InfoCard(
                                        icon = Icons.Default.People,
                                        title = "Bạn bè",
                                        value = "$friendCount",
                                        iconTint = Color(0xFF2196F3)
                                    )
                                }

                                // Location
                                currentUserInfo.location?.let { location ->
                                    InfoCard(
                                        icon = Icons.Default.LocationOn,
                                        title = "Địa điểm",
                                        value = location,
                                        iconTint = Color(0xFF4CAF50)
                                    )
                                }

                                // Hometown
                                currentUserInfo.hometown?.let { hometown ->
                                    InfoCard(
                                        icon = Icons.Default.Home,
                                        title = "Quê quán",
                                        value = hometown,
                                        iconTint = Color(0xFFFF9800)
                                    )
                                }

                                // Birth year
                                currentUserInfo.birthYear?.let { birthYear ->
                                    InfoCard(
                                        icon = Icons.Default.Cake,
                                        title = "Năm sinh",
                                        value = "$birthYear",
                                        iconTint = Color(0xFFE91E63)
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    title: String,
    value: String,
    iconTint: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = Color.Black.copy(alpha = 0.1f)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }
        }
    }
}
