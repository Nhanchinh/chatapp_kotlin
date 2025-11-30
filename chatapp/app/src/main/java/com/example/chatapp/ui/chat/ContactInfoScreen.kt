package com.example.chatapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.clickable
import androidx.navigation.NavController
import com.example.chatapp.ui.navigation.NavRoutes
import com.example.chatapp.viewmodel.ChatViewModel
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactInfoScreen(
    contactName: String,
    contactId: String? = null,
    conversationId: String? = null,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onDeleteConversation: () -> Unit,
    navController: NavController? = null
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thông tin",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2196F3),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                navigationIcon = {}
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            // Profile section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF90CAF9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contactName.firstOrNull()?.uppercase() ?: "?",
                            color = Color.White,
                            style = MaterialTheme.typography.displayMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = contactName,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Encrypted badge
                    Row(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFE8F5E9),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Được mã hóa đầu cuối",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
            
            // Actions section
            item {
                Divider()
                
                // Media files
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = conversationId != null) {
                            if (conversationId != null) {
                                navController?.navigate(
                                    NavRoutes.MediaGallery.createRoute(
                                        conversationId,
                                        contactName
                                    )
                                )
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Xem file phương tiện, file và liên kết",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Tất cả",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                
                Divider()
            }
            
            // Search in conversation
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Tìm kiếm trong cuộc trò chuyện",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Divider()
            }
            
            // Personal page
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = contactId != null) {
                            contactId?.let { userId ->
                                val encodedUserId = Uri.encode(userId)
                                navController?.navigate("otherprofile/$encodedUserId")
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Trang cá nhân",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Divider()
            }
            
            // Delete conversation
            if (conversationId != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDeleteDialog = true
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Xóa cuộc trò chuyện",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFE91E63)
                        )
                    }
                    
                    Divider()
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showDeleteDialog = false
                    deleteError = null
                }
            },
            title = {
                Text("Xóa cuộc trò chuyện")
            },
            text = {
                Column {
                    Text("Bạn có chắc chắn muốn xóa cuộc trò chuyện với $contactName?")
                    if (deleteError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = deleteError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (conversationId != null && !isDeleting) {
                            isDeleting = true
                            deleteError = null
                            chatViewModel.deleteConversation(
                                conversationId = conversationId,
                                onSuccess = {
                                    showDeleteDialog = false
                                    isDeleting = false
                                    onDeleteConversation()
                                },
                                onError = { error ->
                                    deleteError = error
                                    isDeleting = false
                                }
                            )
                        }
                    },
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            "Xóa",
                            color = Color(0xFFE91E63)
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        deleteError = null
                    },
                    enabled = !isDeleting
                ) {
                    Text("Hủy")
                }
            }
        )
    }
}

