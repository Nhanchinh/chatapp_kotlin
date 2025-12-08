package com.example.chatapp.ui.chat

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Reply
import com.example.chatapp.data.model.MediaStatus
import com.example.chatapp.data.model.Message
import com.example.chatapp.ui.common.KeyboardDismissWrapper
import com.example.chatapp.utils.rememberImagePickerLauncher
import com.example.chatapp.viewmodel.ChatViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    contactId: String,
    contactName: String?,
    conversationId: String?,
    onBack: () -> Unit,
    onInfoClick: () -> Unit = {},
    onMediaClick: (messageId: String, mediaId: String, conversationId: String, mimeType: String?) -> Unit = { _, _, _, _ -> }
) {
    val messages by chatViewModel.messages.collectAsStateWithLifecycle()
    val isLoading by chatViewModel.messagesLoading.collectAsStateWithLifecycle()
    val typingUsers by chatViewModel.typingUsers.collectAsStateWithLifecycle()
    val errorMessage by chatViewModel.messagesError.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var messageText by rememberSaveable { mutableStateOf("") }
    var hasSentTyping by remember { mutableStateOf(false) }
    var isTextFieldFocused by remember { mutableStateOf(false) }
    var lastMessageCount by remember { mutableStateOf(0) }
    var isInitialLoad by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }  // Message to delete (for confirmation dialog)
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }  // Message being replied to
    var showMessageActions by remember { mutableStateOf<Message?>(null) }  // Show action menu for message
    var showMoreMenu by remember { mutableStateOf<Message?>(null) }  // Show "More" submenu
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }  // Message ID being highlighted
    val scope = rememberCoroutineScope()
    val imagePickerLauncher = rememberImagePickerLauncher { uri ->
        uri?.let { chatViewModel.sendImage(it) }
    }
    
    // Nested scroll connection to detect pull-to-refresh
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Check if we're at the top and pulling down (available.y > 0 means pulling down)
                val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (isAtTop && available.y > 0 && !isRefreshing && !isLoading) {
                    // User is pulling down from top - trigger refresh
                    isRefreshing = true
                    scope.launch {
                        // Refresh messages by reopening conversation
                        chatViewModel.openConversation(conversationId, contactId, contactName)
                    }
                }
                return Offset.Zero
            }
        }
    }
    
    // End refresh when loading completes
    LaunchedEffect(isLoading) {
        if (isRefreshing && !isLoading) {
            delay(300) // Small delay to ensure UI updates
            isRefreshing = false
        }
    }

    LaunchedEffect(contactId, conversationId) {
        chatViewModel.openConversation(conversationId, contactId, contactName)
        lastMessageCount = 0
        isInitialLoad = true
    }

    // Only scroll to bottom when new messages are added (not on initial load or app restart)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val currentCount = messages.size
            // Only auto-scroll if new messages were added (not on initial load)
            if (!isInitialLoad && currentCount > lastMessageCount) {
                delay(100) // Small delay to ensure list is rendered
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.lastIndex)
                }
            }
            // Mark initial load as complete after first render
            if (isInitialLoad) {
                isInitialLoad = false
            }
            lastMessageCount = currentCount
        } else {
            lastMessageCount = 0
            isInitialLoad = true
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

                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .nestedScroll(nestedScrollConnection)
                            .clickable { 
                                // Click outside → dismiss highlight
                                highlightedMessageId = null 
                            },
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 12.dp,
                            end = 16.dp,
                            bottom = 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pull to refresh indicator
                        if (isRefreshing) {
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
                    // Empty state - only show when no messages and not loading
                    if (!isLoading && messages.isEmpty() && errorMessage == null) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Chat,
                                        contentDescription = null,
                                        modifier = Modifier.size(80.dp),
                                        tint = Color(0xFFBDBDBD)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Chưa có tin nhắn nào",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF424242)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Bắt đầu cuộc trò chuyện với ${chatViewModel.currentContactName.collectAsStateWithLifecycle().value ?: contactName ?: contactId}!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF757575),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                    
                    items(messages) { message ->
                        MessageBubble(
                            message = message,
                            onRetryMedia = { chatViewModel.retryDownloadMedia(it.id) },
                            onMediaClick = {
                                val mediaId = it.mediaId
                                val convoId = it.conversationId
                                if (mediaId != null && !convoId.isNullOrBlank()) {
                                    onMediaClick(it.id, mediaId, convoId, it.mediaMimeType)
                                }
                            },
                            onLongPress = { msg ->
                                // Show action menu for own messages (not deleted) or any message to reply
                                if (!msg.deleted) {
                                    showMessageActions = msg
                                }
                            },
                            onReplyClick = { replyToMessageId ->
                                // Scroll to the replied message and highlight it
                                val index = messages.indexOfFirst { it.id == replyToMessageId }
                                if (index != -1) {
                                    scope.launch {
                                        listState.animateScrollToItem(index)
                                        // Highlight the message
                                        highlightedMessageId = replyToMessageId
                                        // Auto-clear highlight after 2 seconds
                                        kotlinx.coroutines.delay(2000)
                                        highlightedMessageId = null
                                    }
                                }
                            },
                            allMessages = messages,
                            isHighlighted = message.id == highlightedMessageId,
                            onDismissHighlight = { highlightedMessageId = null }
                        )
                    }
                    item {
                        if (typingUsers.isNotEmpty()) {
                            TypingIndicator()
                        }
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
                            chatViewModel.sendMessage(trimmed, replyTo = replyingToMessage?.id)
                            messageText = ""
                            replyingToMessage = null  // Clear reply after sending
                            if (hasSentTyping) {
                                hasSentTyping = false
                                chatViewModel.sendTyping(false)
                            }
                            // Scroll to bottom after sending - will be handled by LaunchedEffect
                        }
                    },
                    onLike = {
                        chatViewModel.sendMessage("👍")
                    },
                    onAttach = { imagePickerLauncher.launch("image/*") },
                    onFocusChange = { focused ->
                        isTextFieldFocused = focused
                    },
                    replyingToMessage = replyingToMessage,
                    onClearReply = { replyingToMessage = null },
                    modifier = Modifier.imePadding()
                )
            }
        }
        
        // Bottom sheet actions (Reactions / Reply / Copy / Translate / Delete)
        val clipboardManager = LocalClipboardManager.current
        if (showMessageActions != null) {
            val msg = showMessageActions!!
            ModalBottomSheet(
                onDismissRequest = { showMessageActions = null },
                sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Reaction picker row (Messenger-like)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val commonEmojis = listOf("❤️", "😂", "😮", "😢", "😡", "👍", "🙏")
                        commonEmojis.forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 28.sp,
                                modifier = Modifier
                                    .clickable {
                                        chatViewModel.reactToMessage(msg.id, emoji)
                                        showMessageActions = null
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }

                    // Action buttons row
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ActionChip(
                            icon = Icons.Default.Reply,
                            label = "Trả lời"
                        ) {
                            replyingToMessage = msg
                            showMessageActions = null
                        }
                        ActionChip(
                            icon = Icons.Default.ContentCopy,
                            label = "Sao chép"
                        ) {
                            clipboardManager.setText(AnnotatedString(msg.text))
                            showMessageActions = null
                        }
                        ActionChip(
                            icon = Icons.Default.Translate,
                            label = "Dịch"
                        ) {
                            // Placeholder: hook into translation later
                            showMessageActions = null
                        }
                        ActionChip(
                            icon = Icons.Default.MoreHoriz,
                            label = "Khác"
                        ) {
                            // Show "More" submenu
                            showMoreMenu = msg
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        
        // "More" submenu (with Delete option)
        if (showMoreMenu != null) {
            val msg = showMoreMenu!!
            ModalBottomSheet(
                onDismissRequest = { showMoreMenu = null },
                sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Delete option (only for own messages)
                    if (msg.isFromMe) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showMoreMenu = null
                                    showMessageActions = null  // Close main menu too
                                    messageToDelete = msg
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFFE91E63),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Xóa tin nhắn",
                                color = Color(0xFFE91E63),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        
        // Delete message confirmation dialog
        messageToDelete?.let { msg ->
            AlertDialog(
                onDismissRequest = { messageToDelete = null },
                title = { Text("Xóa tin nhắn") },
                text = { Text("Bạn có chắc chắn muốn xóa tin nhắn này? Tin nhắn sẽ bị thu hồi và không thể khôi phục.") },
                confirmButton = {
                    Button(
                        onClick = {
                            chatViewModel.deleteMessage(msg.id,
                                onSuccess = {
                                    messageToDelete = null
                                },
                                onError = { error ->
                                    messageToDelete = null
                                    // Could show snackbar here
                                }
                            )
                        }
                    ) {
                        Text("Xóa")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { messageToDelete = null }
                    ) {
                        Text("Hủy")
                    }
                }
            )
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
private fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    onRetryMedia: (Message) -> Unit,
    onMediaClick: ((Message) -> Unit)? = null,
    onLongPress: ((Message) -> Unit)? = null,
    onReplyClick: ((String) -> Unit)? = null,
    allMessages: List<Message> = emptyList(),  // To find replied message
    isHighlighted: Boolean = false,
    onDismissHighlight: () -> Unit = {}
) {
    val isFromMe = message.isFromMe
    val isDeleted = message.deleted
    val repliedMessage = message.replyTo?.let { replyId ->
        allMessages.find { it.id == replyId }
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!isDeleted && onLongPress != null) {
                    Modifier.combinedClickable(
                        onClick = { 
                            // Click outside highlighted message → dismiss highlight
                            if (isHighlighted) {
                                onDismissHighlight()
                            }
                        },
                        onLongClick = { onLongPress(message) }
                    )
                } else {
                    Modifier.clickable {
                        // Click outside highlighted message → dismiss highlight
                        if (isHighlighted) {
                            onDismissHighlight()
                        }
                    }
                }
            )
            .then(
                if (isHighlighted) {
                    Modifier.background(
                        color = Color(0xFFFFD700).copy(alpha = 0.2f),  // Light yellow highlight
                        shape = RoundedCornerShape(8.dp)
                    ).padding(4.dp)
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        if (!isFromMe) {
            AvatarCircle(initial = (message.senderName ?: "?").firstOrNull()?.uppercaseChar() ?: '?')
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start) {
            // Reply preview (if this message is replying to another)
            if (repliedMessage != null) {
                // Logic: Determine who is being replied to from current user's perspective
                val replyToText = when {
                    isFromMe && repliedMessage.isFromMe -> "chính mình"
                    isFromMe && !repliedMessage.isFromMe -> repliedMessage.senderName ?: "người khác"
                    !isFromMe && repliedMessage.isFromMe -> "bạn"
                    else -> repliedMessage.senderName ?: "người khác"
                }
                
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .background(
                            color = Color(0xFFE0E0E0).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .clickable {
                            message.replyTo?.let { replyToId ->
                                onReplyClick?.invoke(replyToId)
                            }
                        }
                ) {
                    Column {
                        Text(
                            text = "Trả lời $replyToText",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isFromMe) Color(0xFF1976D2) else Color(0xFF424242)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (repliedMessage.deleted) "Tin nhắn đã bị xóa" 
                                  else if (repliedMessage.mediaId != null) "📷 Hình ảnh"
                                  else repliedMessage.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
            
            if (message.mediaId != null) {
                MediaPreview(
                    message = message,
                    isFromMe = isFromMe,
                    onRetry = { onRetryMedia(message) },
                    onClick = { onMediaClick?.invoke(message) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            val shouldShowText = message.mediaId == null && message.text.isNotBlank()
            if (shouldShowText) {
                Box(
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isDeleted) {
                                    Color(0xFFE0E0E0)  // Gray background for deleted messages
                                } else if (isFromMe) {
                                    Color(0xFF2196F3)
                                } else {
                                    Color(0xFFE5E5EA)
                                },
                                shape = if (repliedMessage != null) {
                                    RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp, topStart = 4.dp, topEnd = 4.dp)
                                } else {
                                    RoundedCornerShape(16.dp)
                                }
                            )
                            .padding(
                                PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 8.dp,
                                    bottom = if (message.reactions.isNullOrEmpty()) 8.dp else 16.dp  // Extra padding nếu có reactions
                                )
                            )
                    ) {
                        Text(
                            text = message.text,
                            color = if (isDeleted) {
                                Color(0xFF757575)  // Gray text for deleted messages
                            } else if (isFromMe) {
                                Color.White
                            } else {
                                Color.Black
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = if (isDeleted) FontStyle.Italic else FontStyle.Normal
                            )
                        )
                    }
                    
                    // Reactions ở góc dưới (như Messenger) - ra mép, không che chữ
                    message.reactions?.let { reactions ->
                        if (reactions.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .align(if (isFromMe) Alignment.BottomStart else Alignment.BottomEnd)
                                    .offset(
                                        x = if (isFromMe) (-2).dp else 2.dp,  // Ra mép ngoài
                                        y = 4.dp  // Gần mép dưới hơn
                                    )
                                    .background(
                                        color = Color.White,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            ) {
                                // Group reactions by emoji và chỉ hiển thị emoji (không hiện count)
                                val emojiGroups = reactions.entries.groupBy { it.value }
                                emojiGroups.forEach { (emoji, _) ->
                                    Text(
                                        text = emoji,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(horizontal = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // For media messages, show reactions below
                message.reactions?.let { reactions ->
                    if (reactions.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .background(
                                    color = Color.White,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        ) {
                            val emojiGroups = reactions.entries.groupBy { it.value }
                            emojiGroups.forEach { (emoji, _) ->
                                Text(
                                    text = emoji,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 1.dp)
                                )
                            }
                        }
                    }
                }
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
private fun ReactionChip(
    emoji: String,
    count: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFE0E0E0).copy(alpha = 0.6f),
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = emoji, fontSize = 14.sp)
            if (count > 1) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = count.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFF2F2F2)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF2196F3),
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
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
    onAttach: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
    replyingToMessage: Message? = null,
    onClearReply: () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Reply preview bar (if replying to a message)
        if (replyingToMessage != null) {
            val replyToText = if (replyingToMessage.isFromMe) "chính mình" else (replyingToMessage.senderName ?: "người khác")
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Đang trả lời $replyToText",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (replyingToMessage.mediaId != null) "📷 Hình ảnh" else replyingToMessage.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onClearReply) {
                    Icon(
                        imageVector = Icons.Default.Chat,  // Use a close icon if available
                        contentDescription = "Hủy trả lời",
                        tint = Color.Gray
                    )
                }
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        IconButton(onClick = onAttach) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "Gửi ảnh",
                tint = Color(0xFF2196F3)
            )
        }

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
}

@Composable
private fun MediaPreview(
    message: Message,
    isFromMe: Boolean,
    onRetry: () -> Unit,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val backgroundColor = if (isFromMe) Color(0xFF1976D2) else Color(0xFFE0E0E0)
    val contentModifier = Modifier
        .widthIn(max = 280.dp)
        .clip(shape)

    when (message.mediaStatus) {
        MediaStatus.READY -> {
            val uri = message.mediaLocalPath?.let { resolveMediaUri(it) }
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Hình ảnh",
                    modifier = contentModifier.clickable { onClick() },
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = contentModifier
                        .background(backgroundColor)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Không tìm thấy ảnh", color = Color.White)
                }
            }
        }
        MediaStatus.UPLOADING -> {
            Box(
                modifier = contentModifier
                    .background(backgroundColor)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Đang gửi ảnh...", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        MediaStatus.DOWNLOADING -> {
            Box(
                modifier = contentModifier
                    .background(backgroundColor)
                    .padding(16.dp)
                    .clickable { onRetry() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Đang tải ảnh...", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        MediaStatus.FAILED -> {
            Box(
                modifier = contentModifier
                    .background(backgroundColor)
                    .padding(16.dp)
                    .clickable { onRetry() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tải ảnh thất bại\nNhấn để thử lại",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
        else -> {}
    }
}

private fun resolveMediaUri(path: String): Uri {
    return if (path.startsWith("content://") || path.startsWith("file://")) {
        Uri.parse(path)
    } else {
        Uri.fromFile(File(path))
    }
}

