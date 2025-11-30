package com.example.chatapp.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chatapp.ui.common.KeyboardDismissWrapper
import com.example.chatapp.ui.navigation.NavRoutes
import com.example.chatapp.viewmodel.AuthViewModel
import com.example.chatapp.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController? = null,
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    onLogout: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.CHATS) }
    var query by rememberSaveable { mutableStateOf("") }
    var friendRequestsCount by remember { mutableStateOf(0) }

    val conversations by chatViewModel.conversations.collectAsStateWithLifecycle()
    val conversationsLoading by chatViewModel.conversationsLoading.collectAsStateWithLifecycle()
    val conversationsError by chatViewModel.conversationsError.collectAsStateWithLifecycle()
    val myUserId by chatViewModel.myUserId.collectAsStateWithLifecycle()
    val friendsList by chatViewModel.friendsList.collectAsStateWithLifecycle()
    
    var isRefreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Reset refreshing state when screen is first composed
        isRefreshing = false
        chatViewModel.refreshConversations()
        chatViewModel.refreshFriendsList()
    }
    
    // Nested scroll connection to detect pull-to-refresh
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Check if we're at the top and pulling down (available.y > 0 means pulling down)
                val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (isAtTop && available.y > 0 && !isRefreshing && !conversationsLoading) {
                    // User is pulling down from top - trigger refresh
                    isRefreshing = true
                    scope.launch {
                        chatViewModel.refreshConversations()
                        chatViewModel.refreshFriendsList()
                        // Also refresh friend requests count
                        try {
                            val context = navController?.context
                            if (context != null) {
                                val auth = com.example.chatapp.data.local.AuthManager(context)
                                val token = auth.getValidAccessToken()
                                if (token != null) {
                                    val resp = com.example.chatapp.data.remote.ApiClient.apiService.getFriendRequests("Bearer $token")
                                    friendRequestsCount = resp.requests.size
                                }
                            }
                        } catch (_: Exception) {
                            friendRequestsCount = 0
                        }
                        // Wait a bit for loading to complete
                        delay(500)
                        isRefreshing = false
                    }
                }
                return Offset.Zero
            }
        }
    }
    
    // End refresh when loading completes
    LaunchedEffect(conversationsLoading) {
        if (isRefreshing && !conversationsLoading) {
            delay(300)
            isRefreshing = false
        }
    }
    
    // Reset refreshing state when conversations finish initial load
    LaunchedEffect(conversationsLoading, conversations.size) {
        // If we're not actively refreshing but loading is false and we have conversations,
        // make sure isRefreshing is false (in case it got stuck)
        if (!isRefreshing && !conversationsLoading && conversations.isNotEmpty()) {
            isRefreshing = false
        }
    }

    // Load friend requests count
    LaunchedEffect(Unit) {
        try {
            val context = navController?.context ?: return@LaunchedEffect
            val auth = com.example.chatapp.data.local.AuthManager(context)
            val token = auth.getValidAccessToken()
            if (token != null) {
                val resp = com.example.chatapp.data.remote.ApiClient.apiService.getFriendRequests("Bearer $token")
                friendRequestsCount = resp.requests.size
            }
        } catch (_: Exception) {
            friendRequestsCount = 0
        }
    }

    Scaffold(
        bottomBar = { BottomNav(selected = selectedTab, onSelected = { selectedTab = it }, friendRequestsCount = friendRequestsCount) }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            when (selectedTab) {
                HomeTab.CHATS -> {
                    KeyboardDismissWrapper(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header with gradient
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF667EEA),
                                                Color(0xFF764BA2)
                                            )
                                        )
                                    )
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "Đoạn chat",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = query,
                                        onValueChange = { query = it },
                                        leadingIcon = { 
                                            Icon(
                                                Icons.Default.Search, 
                                                contentDescription = null,
                                                tint = Color(0xFF667EEA)
                                            ) 
                                        },
                                        placeholder = { Text("Tìm kiếm") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(24.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedContainerColor = Color.White,
                                            focusedContainerColor = Color.White,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedBorderColor = Color(0xFF667EEA)
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { /* noop */ })
                                    )
                                }
                            }

                            // Friends stories row
                            if (friendsList.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    friendsList.forEach { friend ->
                                        val friendId = friend.id ?: return@forEach
                                        val friendName = friend.fullName ?: friend.email ?: "Unknown"
                                        StoryAvatar(
                                            name = friendName,
                                            online = friend.isOnline ?: false,
                                            onClick = {
                                                query = ""
                                                navController?.navigate(
                                                    NavRoutes.Chat.createRoute(
                                                        contactId = friendId,
                                                        contactName = friendName,
                                                        conversationId = null
                                                    )
                                                )
                                            }
                                        )
                                    }
                                }
                                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                            }

                            if (conversationsError != null) {
                                Text(
                                    text = conversationsError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            if (conversationsLoading && conversations.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .nestedScroll(nestedScrollConnection),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    // Pull to refresh indicator - only show when actively refreshing (not initial load)
                                    if (isRefreshing && !conversationsLoading) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = Color(0xFF667EEA),
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    val filtered = conversations.filter {
                                        query.isBlank() ||
                                            it.name.contains(query, ignoreCase = true) ||
                                            it.lastMessage.contains(query, ignoreCase = true)
                                    }
                                    
                                    if (filtered.isEmpty() && !conversationsLoading) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = "💬",
                                                        fontSize = 64.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Text(
                                                        text = if (query.isBlank()) "Chưa có cuộc trò chuyện nào" else "Không tìm thấy",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = Color(0xFF757575)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    items(filtered) { conversation ->
                                        val contactId = conversation.participants.firstOrNull { it != myUserId } ?: conversation.name
                                        ConversationItem(
                                            name = conversation.name,
                                            lastMessage = conversation.lastMessage,
                                            time = conversation.lastTime,
                                            isOnline = conversation.isOnline,
                                            unreadCount = conversation.unreadCount,
                                            onClick = {
                                                query = ""
                                                navController?.navigate(
                                                    NavRoutes.Chat.createRoute(
                                                        contactId = contactId,
                                                        contactName = conversation.name,
                                                        conversationId = conversation.id
                                                    )
                                                )
                                            }
                                        )
                                    }
                                    item { Spacer(modifier = Modifier.height(72.dp)) }
                                }
                            }
                        }
                    }
                }
                HomeTab.CONTACTS -> {
                    ContactsScreen(
                        navController = navController, 
                        chatViewModel = chatViewModel,
                        onFriendRequestsCountChange = { count -> friendRequestsCount = count }
                    )
                }
                HomeTab.MENU -> {
                    MenuScreen(navController = navController, authViewModel = authViewModel, onLogout = onLogout)
                }
            }
        }
    }
}


