package com.example.chatapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.model.Friend
import com.example.chatapp.data.model.FriendRequest
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.model.UserDto
import com.example.chatapp.ui.navigation.NavRoutes
import com.example.chatapp.utils.formatTimeAgo
import com.example.chatapp.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import retrofit2.HttpException
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.chatapp.utils.UrlHelper
import androidx.compose.foundation.clickable
import android.net.Uri
import com.example.chatapp.viewmodel.AuthViewModel

private enum class ContactsTab {
    FRIENDS,
    REQUESTS
}

private enum class ContactsSearchMode { FRIENDS, USERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    navController: NavController? = null,
    chatViewModel: ChatViewModel? = null,
    authViewModel: AuthViewModel? = null,
    onFriendRequestsCountChange: (Int) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(ContactsTab.FRIENDS) }
    var friendRequestsCount by remember { mutableStateOf(0) }
    val context = LocalContext.current
    
    // Load friend requests count immediately when screen is created
    // Also refresh when selectedTab changes to REQUESTS
    LaunchedEffect(Unit, selectedTab) {
        try {
            val auth = AuthManager(context)
            val token = auth.getValidAccessToken()
            if (token != null) {
                val resp = ApiClient.apiService.getFriendRequests("Bearer $token")
                val count = resp.requests.size
                friendRequestsCount = count
                onFriendRequestsCountChange(count)
            } else {
                friendRequestsCount = 0
                onFriendRequestsCountChange(0)
            }
        } catch (_: Exception) {
            friendRequestsCount = 0
            onFriendRequestsCountChange(0)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header with gradient background
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
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Danh bạ",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                            color = Color.White,
                            height = 3.dp
                        )
                    },
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == ContactsTab.FRIENDS,
                        onClick = { selectedTab = ContactsTab.FRIENDS },
                        text = { 
                            Text(
                                "Bạn bè",
                                fontWeight = if (selectedTab == ContactsTab.FRIENDS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selectedContentColor = Color.White,
                        unselectedContentColor = Color.White.copy(alpha = 0.7f)
                    )
                    Tab(
                        selected = selectedTab == ContactsTab.REQUESTS,
                        onClick = { selectedTab = ContactsTab.REQUESTS },
                        text = { 
                            Box {
                                Text(
                                    "Lời mời kết bạn",
                                    fontWeight = if (selectedTab == ContactsTab.REQUESTS) FontWeight.Bold else FontWeight.Normal
                                )
                                if (friendRequestsCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 12.dp, y = (-4).dp)
                                            .size(18.dp)
                                            .background(Color(0xFFFF6B6B), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (friendRequestsCount > 99) "99+" else friendRequestsCount.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        },
                        selectedContentColor = Color.White,
                        unselectedContentColor = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Content based on selected tab
        when (selectedTab) {
            ContactsTab.FRIENDS -> {
                FriendsListTabContent(
                    authViewModel = authViewModel,
                    onMessageClick = { friendId, friendName ->
                        navController?.navigate(
                            NavRoutes.Chat.createRoute(
                                contactId = friendId,
                                contactName = friendName,
                                conversationId = null
                            )
                        )
                    },
                    onAvatarClick = { friendId ->
                        val encodedUserId = Uri.encode(friendId)
                        navController?.navigate("otherprofile/$encodedUserId")
                    }
                )
            }
            ContactsTab.REQUESTS -> {
                FriendRequestsTabContent(
                    onCountChange = { count ->
                        friendRequestsCount = count
                        onFriendRequestsCountChange(count)
                    }
                )
            }
        }
    }
}

@Composable
private fun FriendsListTabContent(
    authViewModel: AuthViewModel? = null,
    onMessageClick: (String, String) -> Unit,
    onAvatarClick: (String) -> Unit = {}
) {
    var friends by remember { mutableStateOf(listOf<Friend>()) }
    var isLoadingFriends by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var userResults by remember { mutableStateOf(listOf<UserDto>()) }
    var isSearchingUsers by remember { mutableStateOf(false) }
    var addingUserId by remember { mutableStateOf<String?>(null) }
    var invitedIds by remember { mutableStateOf(setOf<String>()) }
    var unfriendingId by remember { mutableStateOf<String?>(null) }
    var searchMode by remember { mutableStateOf(ContactsSearchMode.FRIENDS) }
    var modeMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val formatLastSeen: (String) -> String = { isoTimestamp ->
        try {
            val formats = listOf(
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            )
            
            var date: java.util.Date? = null
            for (format in formats) {
                try {
                    format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    date = format.parse(isoTimestamp)
                    if (date != null) break
                } catch (e: Exception) {
                    continue
                }
            }
            
            if (date != null) {
                val now = java.util.Date()
                val diffMs = now.time - date.time
                val diffMins = diffMs / (1000 * 60)
                val diffHours = diffMs / (1000 * 60 * 60)
                val diffDays = diffMs / (1000 * 60 * 60 * 24)
                
                when {
                    diffMins < 1 -> "Vừa xong"
                    diffMins < 60 -> "${diffMins.toInt()} phút"
                    diffHours < 24 -> "${diffHours.toInt()} giờ"
                    diffDays < 7 -> "${diffDays.toInt()} ngày"
                    else -> {
                        val outputFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                        outputFormat.format(date)
                    }
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    LaunchedEffect(Unit) {
        isLoadingFriends = true
        try {
            val auth = AuthManager(context)
            val token = auth.getValidAccessToken()
            if (token != null) {
                val resp = ApiClient.apiService.getFriendsList("Bearer $token")
                friends = resp.friends.map { u ->
                    Friend(
                        id = u.id ?: "",
                        name = u.fullName ?: (u.email ?: ""),
                        profileImage = u.avatar,
                        mutualFriends = u.friendCount ?: 0,
                        isOnline = u.isOnline ?: false,
                        lastSeen = u.lastSeen?.let { formatLastSeen(it) }
                    )
                }
            } else {
                friends = emptyList()
            }
        } catch (_: Exception) {
            friends = emptyList()
        } finally {
            isLoadingFriends = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Search bar
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
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF667EEA)) },
                placeholder = { Text(if (searchMode == ContactsSearchMode.FRIENDS) "Tìm bạn bè" else "Tìm người dùng") },
                trailingIcon = {
                    if (searchMode == ContactsSearchMode.USERS && !isSearchingUsers && searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isSearchingUsers = true
                                    try {
                                        val auth = AuthManager(context)
                                        val token = auth.getValidAccessToken()
                                        if (token != null) {
                                            val resp = ApiClient.apiService.searchUsers(
                                                token = "Bearer $token",
                                                query = searchQuery,
                                                limit = 20,
                                                prefix = false
                                            )
                                            userResults = resp.items
                                        } else {
                                            userResults = emptyList()
                                        }
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
                                Icon(Icons.Default.Search, contentDescription = "Tìm", tint = Color(0xFF667EEA))
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedBorderColor = Color(0xFF667EEA),
                    unfocusedContainerColor = Color(0xFFF5F5F5),
                    focusedContainerColor = Color.White
                ),
                singleLine = true
            )

            // Mode dropdown
            Box {
                OutlinedButton(
                    onClick = { modeMenuExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF667EEA)
                    )
                ) {
                    Text(if (searchMode == ContactsSearchMode.FRIENDS) "Bạn bè" else "Người dùng", fontSize = 12.sp)
                }
                DropdownMenu(expanded = modeMenuExpanded, onDismissRequest = { modeMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Bạn bè") },
                        onClick = {
                            searchMode = ContactsSearchMode.FRIENDS
                            modeMenuExpanded = false
                            userResults = emptyList()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Người dùng") },
                        onClick = {
                            searchMode = ContactsSearchMode.USERS
                            modeMenuExpanded = false
                        }
                    )
                }
            }
        }

        // Friends list
        if (isLoadingFriends && friends.isEmpty() && searchQuery.isBlank()) {
            // Loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF667EEA),
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                // User search results section
                if (searchMode == ContactsSearchMode.USERS && userResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Kết quả tìm kiếm",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF667EEA)
                    )
                }
                items(userResults) { u ->
                    ContactsUserSearchItem(
                        user = u,
                        isAdding = addingUserId == (u.id ?: ""),
                        invited = invitedIds.contains(u.id ?: ""),
                        onAvatarClick = {
                            u.id?.let { userId ->
                                val encodedUserId = Uri.encode(userId)
                                onAvatarClick(userId)
                            }
                        },
                        onAddClick = {
                            val targetId = u.id
                            if (targetId != null) {
                                scope.launch {
                                    addingUserId = targetId
                                    try {
                                        val auth = AuthManager(context)
                                        val token = auth.getValidAccessToken()
                                        if (token != null) {
                                            ApiClient.apiService.sendFriendRequest(
                                                token = "Bearer $token",
                                                targetUserId = targetId
                                            )
                                            invitedIds = invitedIds + targetId
                                        }
                                    } catch (e: HttpException) {
                                        if (e.code() == 400) {
                                            invitedIds = invitedIds + targetId
                                        }
                                    } catch (e: Exception) {
                                    } finally {
                                        addingUserId = null
                                    }
                                }
                            }
                        }
                    )
                }
                item { Divider(modifier = Modifier.padding(vertical = 8.dp)) }
            }
            
                val filteredFriends = friends.filter {
                    searchMode != ContactsSearchMode.FRIENDS || searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
                }
                
                // Empty state - only show when not loading, no search query, and no friends
                if (!isLoadingFriends && filteredFriends.isEmpty() && userResults.isEmpty() && searchQuery.isBlank()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color(0xFFBDBDBD)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Danh sách bạn bè trống",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF757575)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Bạn chưa có bạn bè nào.\nHãy tìm kiếm và thêm bạn bè mới!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF9E9E9E),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                
                items(filteredFriends) { friend ->
                    ContactsFriendListItem(
                        friend = friend,
                        onMessageClick = { onMessageClick(friend.id, friend.name) },
                        onAvatarClick = { onAvatarClick(friend.id) },
                        onUnfriend = {
                            unfriendingId = friend.id
                            scope.launch {
                                try {
                                    val auth = AuthManager(context)
                                    val token = auth.getValidAccessToken()
                                    if (token != null) {
                                        ApiClient.apiService.unfriend("Bearer $token", friendId = friend.id)
                                        friends = friends.filter { it.id != friend.id }
                                        // Refresh profile to update friend count
                                        authViewModel?.refreshProfile()
                                    }
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
private fun FriendRequestsTabContent(
    onCountChange: (Int) -> Unit = {}
) {
    var friendRequests by remember { mutableStateOf(listOf<FriendRequest>()) }
    var isLoadingRequests by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoadingRequests = true
        try {
            val auth = AuthManager(context)
            val token = auth.getValidAccessToken()
            if (token != null) {
                val resp = ApiClient.apiService.getFriendRequests("Bearer $token")
                friendRequests = resp.requests.map { r ->
                    val displayName = r.requester?.fullName ?: r.requester?.email ?: (r.fromUser ?: "")
                    FriendRequest(
                        id = r.fromUser ?: r.id ?: "",
                        name = displayName,
                        timeAgo = r.createdAt ?: ""
                    )
                }
                onCountChange(friendRequests.size)
            } else {
                friendRequests = emptyList()
                onCountChange(0)
            }
        } catch (_: Exception) {
            friendRequests = emptyList()
            onCountChange(0)
        } finally {
            isLoadingRequests = false
        }
    }

    if (isLoadingRequests) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color(0xFF667EEA),
                modifier = Modifier.size(48.dp)
            )
        }
    } else if (friendRequests.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFFBDBDBD)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Không có lời mời kết bạn",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF757575)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(friendRequests) { request ->
                ContactsFriendRequestItem(
                    request = request,
                    onAccept = {
                        scope.launch {
                            try {
                                val auth = AuthManager(context)
                                val token = auth.getValidAccessToken()
                                if (token != null) {
                                    ApiClient.apiService.acceptFriendRequest("Bearer $token", fromUserId = request.id)
                                    friendRequests = friendRequests.filter { it.id != request.id }
                                    onCountChange(friendRequests.size)
                                }
                            } catch (_: Exception) {}
                        }
                    },
                    onDelete = {
                        scope.launch {
                            try {
                                val auth = AuthManager(context)
                                val token = auth.getValidAccessToken()
                                if (token != null) {
                                    ApiClient.apiService.cancelOrDeclineFriendRequest("Bearer $token", userId = request.id)
                                    friendRequests = friendRequests.filter { it.id != request.id }
                                    onCountChange(friendRequests.size)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ContactsFriendListItem(
    friend: Friend,
    onMessageClick: () -> Unit,
    onAvatarClick: () -> Unit = {},
    onUnfriend: () -> Unit,
    isUnfriending: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar - clickable to navigate to profile
            Box(modifier = Modifier.size(56.dp).clickable { onAvatarClick() }) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(friend.avatarColor)),
                    contentAlignment = Alignment.Center
                ) {
                    val avatarUrl = UrlHelper.avatar(friend.profileImage)
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = friend.name.firstOrNull()?.uppercase() ?: "?",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (friend.isOnline || (friend.lastSeen != null && !friend.isOnline)) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.BottomEnd)
                            .background(Color.White, CircleShape)
                            .padding(2.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    if (friend.isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${friend.mutualFriends} bạn bè",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            // Message button
            IconButton(
                onClick = onMessageClick,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF667EEA)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = "Message",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // More options
            Box {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More",
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
}

@Composable
private fun ContactsUserSearchItem(
    user: UserDto,
    isAdding: Boolean = false,
    invited: Boolean = false,
    onAvatarClick: () -> Unit = {},
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar - clickable to view profile
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF90CAF9))
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                val avatarUrl = UrlHelper.avatar(user.avatar)
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = (user.fullName ?: user.email ?: "?").firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.fullName ?: "(No name)",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = user.email ?: "",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val btnText = when {
                invited -> "Đã gửi"
                isAdding -> "..."
                else -> "Thêm"
            }
            Button(
                onClick = onAddClick,
                enabled = !isAdding && !invited,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (invited) Color(0xFFBDBDBD) else Color(0xFF667EEA)
                )
            ) {
                Text(btnText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ContactsFriendRequestItem(
    request: FriendRequest,
    onAccept: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                            text = formatTimeAgo(request.timeAgo),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF667EEA)
                    )
                ) {
                    Text("Chấp nhận", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF424242)
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xóa")
                    }
                }
            }
        }
    }
}
