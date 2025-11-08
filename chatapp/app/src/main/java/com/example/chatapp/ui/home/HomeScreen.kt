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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.style.TextAlign
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

    val conversations by chatViewModel.conversations.collectAsStateWithLifecycle()
    val conversationsLoading by chatViewModel.conversationsLoading.collectAsStateWithLifecycle()
    val conversationsError by chatViewModel.conversationsError.collectAsStateWithLifecycle()
    val myUserId by chatViewModel.myUserId.collectAsStateWithLifecycle()
    val friendsList by chatViewModel.friendsList.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        chatViewModel.refreshConversations()
        chatViewModel.refreshFriendsList()
    }

    Scaffold(
        bottomBar = { BottomNav(selected = selectedTab, onSelected = { selectedTab = it }) }
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
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                placeholder = { Text("Tìm kiếm") },
                                singleLine = true,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.LightGray,
                                    focusedContainerColor = Color.LightGray,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { /* noop */ })
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                friendsList.forEach { friend ->
                                    val friendId = friend.id ?: return@forEach
                                    val friendName = friend.fullName ?: friend.email ?: "Unknown"
                                    StoryAvatar(
                                        name = friendName,
                                        online = false, // TODO: Implement online status from API
                                        onClick = {
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

                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()

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

                            LazyColumn(modifier = Modifier.weight(1f)) {
                                val filtered = conversations.filter {
                                    query.isBlank() ||
                                        it.name.contains(query, ignoreCase = true) ||
                                        it.lastMessage.contains(query, ignoreCase = true)
                                }
                                items(filtered) { conversation ->
                                    val contactId = conversation.participants.firstOrNull { it != myUserId } ?: conversation.name
                                    ConversationItem(
                                        name = conversation.name,
                                        lastMessage = conversation.lastMessage,
                                        time = conversation.lastTime,
                                        isOnline = conversation.isOnline,
                                        onClick = {
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
                HomeTab.NEWS -> {
                    NewsScreen()
                }
                HomeTab.MENU -> {
                    MenuScreen(navController = navController, authViewModel = authViewModel, onLogout = onLogout)
                }
            }
        }
    }
}


