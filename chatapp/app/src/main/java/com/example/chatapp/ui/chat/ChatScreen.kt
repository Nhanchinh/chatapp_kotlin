package com.example.chatapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import com.example.chatapp.data.model.Message
import com.example.chatapp.ui.common.KeyboardDismissWrapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contactName: String,
    onBack: () -> Unit,
    onInfoClick: () -> Unit = {}
) {
    // Fake messages - using mutableStateListOf to support adding messages
    var messages = remember {
        mutableStateListOf(
            Message("1", "Chưa chắc đã đỡ đầu", System.currentTimeMillis() - 3600000, false, contactName),
            Message("2", ":V", System.currentTimeMillis() - 3600000, true),
            Message("3", "Ae ăn muộn k", System.currentTimeMillis() - 3400000, false, contactName),
            Message("4", "Tối có ăn ở p k", System.currentTimeMillis() - 3300000, true),
            Message("5", "Ừm 6h tôi nhắn :))", System.currentTimeMillis() - 3200000, false, contactName),
            Message("6", "8h ăn", System.currentTimeMillis() - 3100000, true),
            Message("7", ":))", System.currentTimeMillis() - 3000000, false, contactName),
            Message("8", ":V", System.currentTimeMillis() - 2900000, true),
            Message("9", "Chắc thôi ae ăn đi", System.currentTimeMillis() - 2800000, false, contactName),
            Message("10", "Toi tập xong là 6h15, tằm xong r nấu cơm thôi", System.currentTimeMillis() - 2700000, true),
            Message("11", "Tôi về muộn r", System.currentTimeMillis() - 2600000, false, contactName),
            Message("12", "Oke", System.currentTimeMillis() - 2500000, true),
            Message("13", ":V", System.currentTimeMillis() - 2400000, true),
            Message("14", "Oke", System.currentTimeMillis() - 2300000, true),
            Message("15", "Giờ ms nấu cơm", System.currentTimeMillis() - 2200000, true),
        )
    }
    
    var messageText by remember { mutableStateOf("") }
    
    // LazyListState for scrolling
    val listState = rememberLazyListState()
    
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
                        
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF90CAF9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contactName.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = contactName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        IconButton(onClick = onInfoClick) {
                            Icon(
                                Icons.Default.Info, 
                                contentDescription = "Info",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2196F3),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                navigationIcon = {}
            )
        }
    ) { paddingValues ->
        KeyboardDismissWrapper(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Messages list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        MessageBubble(
                            message = message,
                            isFromMe = message.isFromMe
                        )
                    }
                }
                
                // Auto scroll to bottom when new messages are added
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
                
                // Input bar
                MessageInputBar(
                    messageText = messageText,
                    onMessageTextChange = { messageText = it },
                    onSendClick = {
                        if (messageText.isNotBlank()) {
                            val newMessage = Message(
                                id = "${System.currentTimeMillis()}",
                                text = messageText,
                                timestamp = System.currentTimeMillis(),
                                isFromMe = true
                            )
                            messages.add(newMessage)
                            messageText = ""
                        }
                    },
                    onLikeClick = {
                        val likeMessage = Message(
                            id = "${System.currentTimeMillis()}",
                            text = "👍",
                            timestamp = System.currentTimeMillis(),
                            isFromMe = true
                        )
                        messages.add(likeMessage)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isFromMe: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        if (!isFromMe) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF90CAF9)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (message.senderName ?: "?").firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (isFromMe) Color(0xFF2196F3) else Color(0xFFE5E5EA),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = message.text,
                color = if (isFromMe) Color.White else Color.Black,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun MessageInputBar(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Show Send icon if message text is not blank, otherwise show Like icon
    val showSendIcon = messageText.isNotBlank()
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Add attachment button (will use for image/video later)
        IconButton(onClick = { /* Add attachment */ }) {
            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFF2196F3))
        }
        
        OutlinedTextField(
            value = messageText,
            onValueChange = onMessageTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Nhắn tin") },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent
            ),
            maxLines = 4,
            minLines = 1
        )
        
        IconButton(onClick = { 
            if (showSendIcon) {
                onSendClick()
            } else {
                onLikeClick()
            }
        }) {
            Icon(
                if (showSendIcon) Icons.Default.ArrowForward else Icons.Default.ThumbUp, 
                contentDescription = if (showSendIcon) "Send" else "Like",
                tint = if (showSendIcon) Color(0xFF2196F3) else Color.Unspecified
            )
        }
    }
}

