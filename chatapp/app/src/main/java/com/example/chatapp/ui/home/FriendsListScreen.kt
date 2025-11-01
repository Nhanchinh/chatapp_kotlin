package com.example.chatapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.sp
import com.example.chatapp.data.model.Friend

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsListScreen(
    onBack: () -> Unit = {},
    onMessageClick: (String) -> Unit = {}
) {
    // Fake friends data
    var friends by remember {
        mutableStateOf(
            listOf(
                Friend(
                    id = "1",
                    name = "Hoang Nguyen",
                    mutualFriends = 29,
                    isOnline = true,
                    avatarColor = 0xFF4CAF50
                ),
                Friend(
                    id = "2",
                    name = "Nguyễn Đăng Nam",
                    mutualFriends = 47,
                    lastSeen = "53 phút",
                    avatarColor = 0xFF2196F3
                ),
                Friend(
                    id = "3",
                    name = "Thân Đức Trung",
                    mutualFriends = 2,
                    lastSeen = "27 phút",
                    avatarColor = 0xFF9E9E9E
                ),
                Friend(
                    id = "4",
                    name = "Quang Nguyễn",
                    mutualFriends = 44,
                    lastSeen = "25 phút",
                    avatarColor = 0xFF607D8B
                ),
                Friend(
                    id = "5",
                    name = "Hòa Thân",
                    mutualFriends = 5,
                    lastSeen = "3 giờ",
                    avatarColor = 0xFFFF9800
                ),
                Friend(
                    id = "6",
                    name = "Bùi Đức Trọng",
                    mutualFriends = 40,
                    lastSeen = "25 phút",
                    avatarColor = 0xFFE91E63
                ),
                Friend(
                    id = "7",
                    name = "Nguyễn Mạnh Quyên",
                    mutualFriends = 42,
                    isOnline = true,
                    avatarColor = 0xFF795548
                ),
                Friend(
                    id = "8",
                    name = "Nguyên Nam",
                    mutualFriends = 42,
                    lastSeen = "20 phút",
                    avatarColor = 0xFF9C27B0
                ),
                Friend(
                    id = "9",
                    name = "Nguyen The Anh",
                    mutualFriends = 42,
                    lastSeen = "55 phút",
                    avatarColor = 0xFFFF5722
                )
            )
        )
    }

    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bạn bè") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                placeholder = { Text("Tìm kiếm bạn bè") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Gray,
                    focusedBorderColor = Color(0xFF2196F3)
                ),
                singleLine = true
            )

            // Friends list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                items(friends.filter {
                    searchQuery.isBlank() || it.name.contains(
                        searchQuery,
                        ignoreCase = true
                    )
                }) { friend ->
                    FriendListItem(
                        friend = friend,
                        onMessageClick = { onMessageClick(friend.name) },
                        onMoreClick = { /* Handle more options */ }
                    )
                }
            }
        }
    }
}

@Composable
fun FriendListItem(
    friend: Friend,
    onMessageClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile picture with status indicator
        Box(
            modifier = Modifier.size(56.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(friend.avatarColor)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = friend.name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Online/Last seen indicator
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (friend.isOnline) {
                            Color(0xFF4CAF50) // Green for online
                        } else {
                            Color(0xFF9E9E9E) // Grey for offline
                        }
                    )
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .background(Color.White, CircleShape)
                    .padding(2.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // If online, just show green circle
                // If offline, show time text
                if (friend.lastSeen != null && !friend.isOnline) {
                    Text(
                        text = friend.lastSeen.split(" ")[0], // Take just the number
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name and mutual friends
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = friend.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${friend.mutualFriends} bạn chung",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Message button
        IconButton(
            onClick = onMessageClick,
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = "Message",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // More options button
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = Color.Gray
            )
        }
    }
}

