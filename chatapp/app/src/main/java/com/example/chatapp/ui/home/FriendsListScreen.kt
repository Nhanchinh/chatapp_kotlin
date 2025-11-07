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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.data.model.Friend
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.model.UserDto
import com.example.chatapp.data.local.AuthManager
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
 
private enum class SearchMode { FRIENDS, USERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsListScreen(
    onBack: () -> Unit = {},
    onMessageClick: (String, String) -> Unit = { _, _ -> }
) {
    // Friends loaded from API
    var friends by remember { mutableStateOf(listOf<Friend>()) }

    var searchQuery by remember { mutableStateOf("") }
    var userResults by remember { mutableStateOf(listOf<UserDto>()) }
    var isSearchingUsers by remember { mutableStateOf(false) }
    var addingUserId by remember { mutableStateOf<String?>(null) }
    var invitedIds by remember { mutableStateOf(setOf<String>()) }
    var unfriendingId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchMode by remember { mutableStateOf(SearchMode.FRIENDS) }
    var modeMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Load friends from backend
        try {
            val auth = AuthManager(context)
            val token = auth.getAccessTokenOnce()
            val bearer = token?.let { "Bearer $it" } ?: ""
            val resp = ApiClient.apiService.getFriendsList(bearer)
            friends = resp.friends.map { u ->
                Friend(
                    id = u.id ?: "",
                    name = u.fullName ?: (u.email ?: ""),
                    mutualFriends = u.friendCount ?: 0
                )
            }
        } catch (_: Exception) {
            friends = emptyList()
        }
    }

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
            // Unified Search bar with mode switch (Friends vs Users)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(if (searchMode == SearchMode.FRIENDS) "Tìm bạn bè" else "Tìm người dùng") },
                    trailingIcon = {
                        IconButton(
                            enabled = (searchMode == SearchMode.USERS) && !isSearchingUsers && searchQuery.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    isSearchingUsers = true
                                    try {
                                        val auth = AuthManager(context)
                                        val token = auth.getAccessTokenOnce()
                                        val bearer = token?.let { "Bearer $it" } ?: ""
                                        val resp = ApiClient.apiService.searchUsers(
                                            token = bearer,
                                            query = searchQuery,
                                            limit = 20,
                                            prefix = false
                                        )
                                        userResults = resp.items
                                    } catch (e: Exception) {
                                        userResults = emptyList()
                                    } finally {
                                        isSearchingUsers = false
                                    }
                                }
                            }
                        ) {
                            if (isSearchingUsers) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "Tìm")
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Gray,
                        focusedBorderColor = Color(0xFF2196F3)
                    ),
                    singleLine = true,
                    maxLines = 1
                )

                // Mode dropdown
                Box {
                    OutlinedButton(onClick = { modeMenuExpanded = true }) {
                        Text(if (searchMode == SearchMode.FRIENDS) "Bạn bè" else "Người dùng")
                    }
                    DropdownMenu(expanded = modeMenuExpanded, onDismissRequest = { modeMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Bạn bè") }, onClick = {
                            searchMode = SearchMode.FRIENDS
                            modeMenuExpanded = false
                            userResults = emptyList()
                        })
                        DropdownMenuItem(text = { Text("Người dùng") }, onClick = {
                            searchMode = SearchMode.USERS
                            modeMenuExpanded = false
                            // keep current searchQuery; user can press Tìm to fetch
                        })
                    }
                }
            }

            // Friends list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                // User search results section (only show in Users mode)
                if (searchMode == SearchMode.USERS && userResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "Kết quả người dùng",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(userResults) { u ->
                        UserSearchItem(
                            user = u,
                            isAdding = addingUserId == (u.id ?: ""),
                            invited = invitedIds.contains(u.id ?: ""),
                            onAddClick = add@{
                                val targetId = u.id ?: return@add
                                scope.launch {
                                    addingUserId = targetId
                                    try {
                                        val auth = AuthManager(context)
                                        val token = auth.getAccessTokenOnce()
                                        val bearer = token?.let { "Bearer $it" } ?: ""
                                        val resp = ApiClient.apiService.sendFriendRequest(
                                            token = bearer,
                                            targetUserId = targetId
                                        )
                                        invitedIds = invitedIds + targetId
                                    } catch (e: Exception) {
                                        // ignore for now; could show toast/snackbar
                                    } finally {
                                        addingUserId = null
                                    }
                                }
                            }
                        )
                    }
                    item { Divider(modifier = Modifier.padding(vertical = 8.dp)) }
                }
                items(friends.filter {
                    // Filter friends only in Friends mode; otherwise show all friends below
                    searchMode != SearchMode.FRIENDS || searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
                }) { friend ->
                    FriendListItem(
                        friend = friend,
                        onMessageClick = { onMessageClick(friend.id, friend.name) },
                        onMoreClick = { /* unused */ },
                        onUnfriend = {
                            unfriendingId = friend.id
                            scope.launch {
                                try {
                                    val auth = AuthManager(context)
                                    val token = auth.getAccessTokenOnce()
                                    val bearer = token?.let { "Bearer $it" } ?: ""
                                    ApiClient.apiService.unfriend(bearer, friendId = friend.id)
                                    friends = friends.filter { it.id != friend.id }
                                } catch (_: Exception) {
                                } finally {
                                    unfriendingId = null
                                }
                            }
                        },
                        isUnfriending = unfriendingId == friend.id
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
    onMoreClick: () -> Unit,
    onUnfriend: () -> Unit,
    isUnfriending: Boolean = false
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

        // More options with dropdown
        var expanded by remember { mutableStateOf(false) }
        Box {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = Color.Gray
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(if (isUnfriending) "Đang hủy..." else "Hủy kết bạn") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    enabled = !isUnfriending,
                    onClick = {
                        expanded = false
                        onUnfriend()
                    }
                )
            }
        }
    }
}

@Composable
fun UserSearchItem(user: UserDto, isAdding: Boolean = false, invited: Boolean = false, onAddClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF90CAF9)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (user.fullName ?: user.email ?: "?").firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.fullName ?: "(No name)", fontWeight = FontWeight.Bold)
            Text(text = user.email ?: "", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        val btnText = when {
            invited -> "Đã gửi"
            isAdding -> "..."
            else -> "Thêm"
        }
        TextButton(onClick = onAddClick, enabled = !isAdding && !invited) { Text(btnText) }
    }
}

