package com.example.chatapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.chatapp.ui.navigation.NavRoutes
import com.example.chatapp.viewmodel.AuthViewModel
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.remote.ApiClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(navController: NavController? = null, authViewModel: AuthViewModel, onLogout: () -> Unit) {
    val authState by authViewModel.authState.collectAsState()
    var pendingRequests by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            val context = navController?.context ?: return@LaunchedEffect
            val auth = AuthManager(context)
            val token = auth.getValidAccessToken() ?: return@LaunchedEffect
            val resp = ApiClient.apiService.getFriendRequests("Bearer $token")
            pendingRequests = resp.requests.size
        } catch (_: Exception) { pendingRequests = 0 }
    }
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
                    .clickable {
                        val usernamePath = authState.userEmail ?: authState.userId ?: "me"
                        navController?.navigate("profile/$usernamePath")
                    }
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
                        text = (authState.userFullName ?: authState.userEmail ?: "?")
                            .split(" ")
                            .take(2)
                            .mapNotNull { it.firstOrNull()?.toString() }
                            .joinToString("")
                            .uppercase().ifEmpty { "?" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = authState.userFullName ?: authState.userEmail ?: "Người dùng",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Chuyển trang cá nhân - @" + (authState.userEmail ?: authState.userId ?: "me"),
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Lời mời kết bạn",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (pendingRequests > 0) {
                            Badge(containerColor = Color.Red) {
                                Text(pendingRequests.toString(), color = Color.White)
                            }
                        }
                    }
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
                
                // Quét mã QR (QR Scanner)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController?.navigate(NavRoutes.QRCodeScanner.route) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Quét mã QR",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Divider()
                
                // Mã QR của tôi (My QR Code)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController?.navigate(NavRoutes.MyQRCode.route) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.QrCode,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Mã QR của tôi",
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
