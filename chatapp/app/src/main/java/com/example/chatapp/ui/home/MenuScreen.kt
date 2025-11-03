package com.example.chatapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.chatapp.ui.navigation.NavRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(navController: NavController? = null, onLogout: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Menu") },
                actions = {
                    IconButton(onClick = { /* More options */ }) {
                        Icon(Icons.Default.Apps, contentDescription = "More options")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            // Profile section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController?.navigate("profile/chính.thannhan.50") }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF90CAF9)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CT",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Chính Thân",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Chuyển trang cá nhân - @chính.thannhan.50",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            
            Divider()
            
            // Menu items
            Column {
                // Cài đặt (Settings)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController?.navigate(NavRoutes.Settings.route) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Cài đặt",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Divider()
                
                // Lời mời kết bạn (Friend Requests)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController?.navigate(NavRoutes.FriendRequest.route) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Lời mời kết bạn",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Divider()
                
                // Danh sách bạn bè (Friends List)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController?.navigate(NavRoutes.FriendsList.route) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Danh sách bạn bè",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Divider()
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Logout button
            Button(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Đăng xuất")
            }
        }
    }
}
