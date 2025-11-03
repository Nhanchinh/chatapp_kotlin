package com.example.chatapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userName: String = "Thân Chính",
    username: String = "chính.thannhan.50",
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { /* Search */ }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                modifier = Modifier.background(Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Cover photo area
            Box(modifier = Modifier.fillMaxWidth()) {
                // Cover photo placeholder - reddish-orange leaves
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Color(0xFFE67E22), // Orange background
                            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                        )
                )
                
                // Camera icon on cover photo
                IconButton(
                    onClick = { /* Edit cover photo */ },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Edit cover photo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Profile picture row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Profile picture
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .offset(y = (-30).dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                                .background(Color(0xFFB39DDB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userName.split(" ").map { it.firstOrNull() ?: "" }.joinToString("").uppercase(),
                                color = Color.White,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Camera icon on profile picture
                        IconButton(
                            onClick = { /* Edit profile photo */ },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2196F3))
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Edit profile photo",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    // Friend count
                    Column(
                        modifier = Modifier
                            .align(Alignment.Bottom)
                            .padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "208 người bạn",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // User name with dropdown
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "More options",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Add to story button
                    Button(
                        onClick = { /* Add to story */ },
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Thêm vào tin")
                    }
                    
                    // Edit profile button
                    OutlinedButton(
                        onClick = { /* Edit profile */ },
                        modifier = Modifier.weight(2f)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Chỉnh sửa",
                            color = Color(0xFF2196F3),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    // More options button
                    OutlinedButton(
                        onClick = { /* More options */ },
                        modifier = Modifier.width(48.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Navigation tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf("Bài viết", "Ảnh", "Reels")
                    tabs.forEachIndexed { index, tab ->
                        Button(
                            onClick = { selectedTab = index },
                            modifier = Modifier.weight(1f),
                            colors = if (selectedTab == index) {
                                ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                            } else {
                                ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                            },
                            elevation = null
                        ) {
                            Text(
                                text = tab,
                                color = if (selectedTab == index) Color.White else Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Details section
                Text(
                    text = "Chi tiết",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Education 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Học tại Học Viện Kĩ Thuật Mật Mã",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Education 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Đã học tại Trường THPT Việt Yên số 1",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Location
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Sống tại ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Việt Yên",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // From
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Đến từ ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Bắc Giang",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // About info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* Show about info */ },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Xem thông tin giới thiệu của bạn",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

