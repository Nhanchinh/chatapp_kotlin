package com.example.chatapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chatapp.data.model.FriendRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendRequestScreen(onBack: () -> Unit = {}) {
    // Fake friend requests data
    var friendRequests by remember {
        mutableStateOf(
            listOf(
                FriendRequest(
                    id = "1",
                    name = "Ha Hoang",
                    mutualFriends = 1,
                    timeAgo = "8 tuần",
                    avatarColor = 0xFF4CAF50
                ),
                FriendRequest(
                    id = "2",
                    name = "Nguyễn Quang Thiên",
                    timeAgo = "2 tuần",
                    avatarColor = 0xFF9E9E9E
                ),
                FriendRequest(
                    id = "3",
                    name = "Dung Thân",
                    mutualFriends = 1,
                    timeAgo = "47 tuần",
                    avatarColor = 0xFFFF9800
                ),
                FriendRequest(
                    id = "4",
                    name = "Quý Phùng",
                    mutualFriends = 1,
                    timeAgo = "2 năm",
                    avatarColor = 0xFFE91E63
                ),
                FriendRequest(
                    id = "5",
                    name = "Khánh Vy",
                    timeAgo = "3 năm",
                    avatarColor = 0xFF2196F3
                ),
                FriendRequest(
                    id = "6",
                    name = "Đức Huy",
                    timeAgo = "1 năm",
                    avatarColor = 0xFF9E9E9E
                )
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lời mời kết bạn",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = friendRequests.size.toString(),
                            color = Color.Red,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { /* See all */ }) {
                            Text("Xem tất cả", color = Color(0xFF2196F3))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2196F3),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(friendRequests) { request ->
                FriendRequestItem(
                    request = request,
                    onAccept = {
                        // Remove from list when accepted
                        friendRequests = friendRequests.filter { it.id != request.id }
                    },
                    onDelete = {
                        // Remove from list when deleted
                        friendRequests = friendRequests.filter { it.id != request.id }
                    }
                )
            }
        }
    }
}

@Composable
fun FriendRequestItem(
    request: FriendRequest,
    onAccept: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile picture
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(request.avatarColor)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = request.name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name and details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = request.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (request.mutualFriends != null) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${request.mutualFriends} bạn chung",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Text(
                        text = request.timeAgo,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAccept,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                Text(
                    text = "Chấp nhận",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }

            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF424242),
                    containerColor = Color.Transparent
                ),
                border = null
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Xóa",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Divider()
    }
}

