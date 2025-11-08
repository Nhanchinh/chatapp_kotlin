package com.example.chatapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.onFocusEvent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chatapp.data.model.Message
import com.example.chatapp.ui.common.KeyboardDismissWrapper
import com.example.chatapp.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    contactId: String,
    contactName: String?,
    conversationId: String?,
    onBack: () -> Unit,
    onInfoClick: () -> Unit = {}
) {
    val messages by chatViewModel.messages.collectAsStateWithLifecycle()
    val isLoading by chatViewModel.messagesLoading.collectAsStateWithLifecycle()
    val typingUsers by chatViewModel.typingUsers.collectAsStateWithLifecycle()
    val errorMessage by chatViewModel.messagesError.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var messageText by rememberSaveable { mutableStateOf("") }
    var hasSentTyping by remember { mutableStateOf(false) }
    var isTextFieldFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(contactId, conversationId) {
        chatViewModel.openConversation(conversationId, contactId, contactName)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Scroll to bottom when text field is focused (keyboard opens)
    LaunchedEffect(isTextFieldFocused) {
        if (isTextFieldFocused && messages.isNotEmpty()) {
            delay(300) // Wait for keyboard animation
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            chatViewModel.clearCurrentChat()
        }
    }

    KeyboardDismissWrapper(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    title = chatViewModel.currentContactName.collectAsStateWithLifecycle().value ?: contactName ?: contactId,
                    onBack = onBack,
                    onInfoClick = onInfoClick
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (isLoading && messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        MessageBubble(message = message)
                    }
                    item {
                        if (typingUsers.isNotEmpty()) {
                            TypingIndicator()
                        }
                    }
                }

                // Input bar with IME padding to stay above keyboard
                // This ensures the input bar moves up when keyboard opens
                ChatInputBar(
                    text = messageText,
                    onTextChange = { newValue ->
                        val wasBlank = messageText.isBlank()
                        messageText = newValue
                        val isBlankNow = newValue.isBlank()
                        if (!hasSentTyping && wasBlank && !isBlankNow) {
                            hasSentTyping = true
                            chatViewModel.sendTyping(true)
                        }
                        if (hasSentTyping && isBlankNow) {
                            hasSentTyping = false
                            chatViewModel.sendTyping(false)
                        }
                    },
                    onSend = {
                        val trimmed = messageText.trim()
                        if (trimmed.isNotEmpty()) {
                            chatViewModel.sendMessage(trimmed)
                            messageText = ""
                            if (hasSentTyping) {
                                hasSentTyping = false
                                chatViewModel.sendTyping(false)
                            }
                            // Scroll to bottom after sending
                            scope.launch {
                                if (messages.isNotEmpty()) {
                                    listState.animateScrollToItem(messages.lastIndex)
                                }
                            }
                        }
                    },
                    onLike = {
                        chatViewModel.sendMessage("👍")
                    },
                    onFocusChange = { focused ->
                        isTextFieldFocused = focused
                    },
                    modifier = Modifier.imePadding()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    title: String,
    onBack: () -> Unit,
    onInfoClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarCircle(initial = title.firstOrNull()?.uppercaseChar() ?: '❓')
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        },
        actions = {
            IconButton(onClick = onInfoClick) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF2196F3),
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}

@Composable
private fun AvatarCircle(initial: Char) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFF90CAF9)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial.toString(),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun MessageBubble(message: Message, modifier: Modifier = Modifier) {
    val isFromMe = message.isFromMe
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        if (!isFromMe) {
            AvatarCircle(initial = (message.senderName ?: "?").firstOrNull()?.uppercaseChar() ?: '?')
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        color = if (isFromMe) Color(0xFF2196F3) else Color(0xFFE5E5EA),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.text,
                    color = if (isFromMe) Color.White else Color.Black,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (isFromMe) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            message.seen -> "Đã xem"
                            message.delivered -> "Đã nhận"
                            else -> "Đã gửi"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Text(
        text = "Đang nhập...",
        style = MaterialTheme.typography.bodySmall,
        color = Color.Gray,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
    )
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onLike: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .onFocusEvent { focusState ->
                    onFocusChange(focusState.isFocused)
                },
            placeholder = { Text("Nhắn tin") },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedContainerColor = Color(0xFFF2F2F2),
                focusedContainerColor = Color(0xFFF2F2F2)
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSend() },
                onSend = { onSend() },
                onGo = { onSend() },
                onNext = { onSend() },
                onSearch = { onSend() }
            ),
            maxLines = 4,
            minLines = 1
        )

        IconButton(onClick = { if (text.isBlank()) onLike() else onSend() }) {
            Icon(
                imageVector = if (text.isBlank()) Icons.Default.ThumbUp else Icons.AutoMirrored.Filled.Send,
                contentDescription = if (text.isBlank()) "Like" else "Send",
                tint = if (text.isBlank()) Color(0xFF2196F3) else Color(0xFF2196F3)
            )
        }
    }
}

