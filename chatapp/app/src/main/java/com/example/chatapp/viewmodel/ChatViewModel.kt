package com.example.chatapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.model.Conversation
import com.example.chatapp.data.model.Message
import com.example.chatapp.data.repository.ChatRepository
import com.example.chatapp.data.remote.WebSocketEvent
import com.example.chatapp.data.remote.model.MessageAck
import com.example.chatapp.data.remote.model.UserDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)
    private val authManager = AuthManager(application)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _conversationsLoading = MutableStateFlow(false)
    val conversationsLoading: StateFlow<Boolean> = _conversationsLoading.asStateFlow()

    private val _conversationsError = MutableStateFlow<String?>(null)
    val conversationsError: StateFlow<String?> = _conversationsError.asStateFlow()

    private val _friendsMap = MutableStateFlow<Map<String, String>>(emptyMap())
    private val friendsMap: StateFlow<Map<String, String>> = _friendsMap.asStateFlow()

    private val _friendsList = MutableStateFlow<List<UserDto>>(emptyList())
    val friendsList: StateFlow<List<UserDto>> = _friendsList.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _messagesLoading = MutableStateFlow(false)
    val messagesLoading: StateFlow<Boolean> = _messagesLoading.asStateFlow()

    private val _messagesError = MutableStateFlow<String?>(null)
    val messagesError: StateFlow<String?> = _messagesError.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _currentContactId = MutableStateFlow<String?>(null)
    val currentContactId: StateFlow<String?> = _currentContactId.asStateFlow()

    private val _currentContactName = MutableStateFlow<String?>(null)
    val currentContactName: StateFlow<String?> = _currentContactName.asStateFlow()

    private val _typingUsers = MutableStateFlow<Set<String>>(emptySet())
    val typingUsers: StateFlow<Set<String>> = _typingUsers.asStateFlow()

    private val _webSocketConnected = MutableStateFlow(false)
    val webSocketConnected: StateFlow<Boolean> = _webSocketConnected.asStateFlow()

    private var webSocketJob: Job? = null

    // E2EE: Cache conversations đã decrypt thành công (tránh decrypt lại mỗi lần refresh)
    private val decryptedConversations = mutableSetOf<String>()
    
    // E2EE: Track conversations đã try auto-setup (tránh infinite loop)
    private val autoSetupAttempted = mutableSetOf<String>()

    private val currentUserId: StateFlow<String?> = authManager.userId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val myUserId: StateFlow<String?>
        get() = currentUserId

    init {
        viewModelScope.launch {
            loadFriendsMap()
        }
        refreshConversations()
        
        // Disconnect WebSocket khi user logout (userId = null)
        viewModelScope.launch {
            currentUserId.collect { userId ->
                if (userId == null) {
                    // User đã logout, disconnect WebSocket và clear data
                    disconnectWebSocket()
                    clearCurrentChat()
                    _conversations.value = emptyList()
                    _friendsList.value = emptyList()
                    _friendsMap.value = emptyMap()
                }
            }
        }
    }

    private suspend fun loadFriendsMap(): Map<String, String> {
        return repository.getFriendsList().fold(
            onSuccess = { response ->
                val map = response.friends.associate { friend ->
                    (friend.id ?: "") to (friend.fullName ?: friend.email ?: "Unknown")
                }
                _friendsMap.value = map
                _friendsList.value = response.friends
                map
            },
            onFailure = {
                // Return current map if load fails
                friendsMap.value
            }
        )
    }

    fun refreshFriendsList() {
        viewModelScope.launch {
            loadFriendsMap()
        }
    }

    fun refreshConversations() {
        viewModelScope.launch {
            try {
                // Load friends map first to ensure we have names
                val friends = loadFriendsMap()
                
                _conversationsLoading.value = true
                _conversationsError.value = null
                repository.getConversations().fold(
                    onSuccess = { response ->
                        try {
                            val me = currentUserId.value
                            _conversations.value = response.items.mapNotNull { dto ->
                                try {
                                    val otherParticipant = dto.participants.firstOrNull { it != me }
                                    val displayName = otherParticipant?.let { friends[it] } ?: otherParticipant ?: "Unknown"
                                    val formattedTime = try {
                                        formatTimestamp(dto.lastMessageAt)
                                    } catch (e: Exception) {
                                        android.util.Log.e("ChatViewModel", "Error formatting time for conversation ${dto.id}", e)
                                        ""
                                    }
                                    
                                    // Kiểm tra xem last message có phải do current user gửi không
                                    val isLastMessageFromMe = dto.lastMessageSenderId == me
                                    val lastMessageText = dto.lastMessagePreview.orEmpty()
                                    
                                    // Try to decrypt preview if it's encrypted
                                    val decryptedPreview = if (lastMessageText == "[Encrypted Message]") {
                                        // Only decrypt if not already done (avoid re-decrypt on every refresh)
                                        if (!decryptedConversations.contains(dto.id)) {
                                            tryDecryptConversationPreview(dto.id)
                                            "🔒 Đang giải mã..."  // Temporary while decrypting
                                        } else {
                                            // Already attempted, keep current state
                                            lastMessageText
                                        }
                                    } else {
                                        lastMessageText
                                    }
                                    
                                    val cleanedText = when {
                                        decryptedPreview.isEmpty() -> "Chưa có tin nhắn"
                                        else -> decryptedPreview
                                    }
                                    
                                    val displayMessage = if (isLastMessageFromMe && cleanedText.isNotEmpty() && !cleanedText.startsWith("🔒")) {
                                        "Bạn: $cleanedText"
                                    } else {
                                        cleanedText
                                    }
                                    
                                    Conversation(
                                        id = dto.id,
                                        name = displayName,
                                        lastMessage = displayMessage,
                                        lastTime = formattedTime,
                                        unreadCount = dto.unreadCounters[me] ?: 0,
                                        isOnline = dto.isOnline ?: false, // Safe default: false if Redis not available
                                        participants = dto.participants,
                                        lastMessageAt = dto.lastMessageAt,
                                        lastMessagePreview = dto.lastMessagePreview,
                                        lastMessageSenderId = dto.lastMessageSenderId
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatViewModel", "Error processing conversation ${dto.id}", e)
                                    null // Skip this conversation if there's an error
                                }
                            }
                            _conversationsLoading.value = false
                        } catch (e: Exception) {
                            android.util.Log.e("ChatViewModel", "Error processing conversations", e)
                            _conversationsLoading.value = false
                            _conversationsError.value = "Lỗi xử lý dữ liệu"
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("ChatViewModel", "Error fetching conversations", error)
                        _conversationsLoading.value = false
                        _conversationsError.value = error.message ?: "Không thể tải danh sách cuộc trò chuyện"
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Unexpected error in refreshConversations", e)
                _conversationsLoading.value = false
                _conversationsError.value = "Lỗi không xác định"
            }
        }
    }

    fun openConversation(conversationId: String?, contactId: String, contactName: String?, resumeSince: Long? = null) {
        _currentContactId.value = contactId
        _currentContactName.value = contactName ?: contactId
        _currentConversationId.value = conversationId

        if (conversationId != null) {
            // **CRITICAL**: Setup encryption BEFORE loading messages to avoid decrypt failures
            viewModelScope.launch {
                val me = currentUserId.value
                if (me != null && !repository.isEncryptionAvailable(conversationId)) {
                    val participants = listOf(me, contactId)
                    android.util.Log.d("ChatViewModel", "Setting up E2EE BEFORE loading messages: $conversationId")
                    
                    repository.setupConversationEncryption(conversationId, participants).fold(
                        onSuccess = { success ->
                            if (success) {
                                android.util.Log.d("ChatViewModel", "E2EE setup successful")
                            } else {
                                android.util.Log.w("ChatViewModel", "E2EE setup returned false")
                            }
                        },
                        onFailure = { error ->
                            android.util.Log.e("ChatViewModel", "Error setting up encryption: ${error.message}")
                        }
                    )
                    
                    // Small delay to ensure keys are fully stored
                    kotlinx.coroutines.delay(300)
                }
                
                // Load messages AFTER encryption is setup
                loadMessages(conversationId)
            }
        } else {
            // Try to find existing conversation in the loaded list
            findAndLoadExistingConversation(contactId)
        }

        ensureWebSocketConnected(resumeSince)
    }

    private fun findAndLoadExistingConversation(contactId: String) {
        viewModelScope.launch {
            val me = currentUserId.value ?: return@launch
            
            // First, try to find in already loaded conversations
            var existingConversation = conversations.value.firstOrNull { conversation ->
                val participants = conversation.participants.toSet()
                participants.contains(me) && participants.contains(contactId)
            }
            
            if (existingConversation != null) {
                // Found existing conversation, load its messages
                _currentConversationId.value = existingConversation.id
                loadMessages(existingConversation.id)
                return@launch
            }
            
            // Not found in loaded list, refresh conversations with higher limit to find it
            // Use a higher limit (100) to increase chance of finding the conversation
            _conversationsLoading.value = true
            val friends = friendsMap.value
            repository.getConversations(limit = 100).fold(
                onSuccess = { response ->
                    val updatedConversations = response.items.map { dto ->
                        val otherParticipant = dto.participants.firstOrNull { it != me }
                        val displayName = otherParticipant?.let { friends[it] } ?: otherParticipant ?: "Unknown"
                        Conversation(
                            id = dto.id,
                            name = displayName,
                            lastMessage = dto.lastMessagePreview.orEmpty(),
                            lastTime = formatTimestamp(dto.lastMessageAt),
                            unreadCount = dto.unreadCounters[me] ?: 0,
                            isOnline = dto.isOnline ?: false,
                            participants = dto.participants,
                            lastMessageAt = dto.lastMessageAt,
                            lastMessagePreview = dto.lastMessagePreview
                        )
                    }
                    _conversations.value = updatedConversations
                    _conversationsLoading.value = false
                    
                    // Try to find again after refresh
                    existingConversation = updatedConversations.firstOrNull { conversation ->
                        val participants = conversation.participants.toSet()
                        participants.contains(me) && participants.contains(contactId)
                    }
                    
                    if (existingConversation != null) {
                        // Found existing conversation after refresh
                        _currentConversationId.value = existingConversation.id
                        loadMessages(existingConversation.id)
                    } else {
                        // No existing conversation found - this is a new conversation
                        // Messages will be empty until first message is sent
                        // Backend will auto-create conversation when first message is sent via WebSocket
                        _messages.value = emptyList()
                        _currentConversationId.value = null
                    }
                },
                onFailure = { error ->
                    _conversationsLoading.value = false
                    // Even if refresh fails, still allow user to start new conversation
                    _messages.value = emptyList()
                    _currentConversationId.value = null
                }
            )
        }
    }

    fun clearCurrentChat() {
        _currentConversationId.value = null
        _currentContactId.value = null
        _currentContactName.value = null
        _messages.value = emptyList()
        _typingUsers.value = emptySet()
    }

    private fun loadMessages(conversationId: String, limit: Int = 50, cursor: String? = null) {
        viewModelScope.launch {
            _messagesLoading.value = true
            _messagesError.value = null
            // Ensure friends map is loaded for name resolution
            val friends = friendsMap.value
            repository.getMessages(conversationId, limit, cursor).fold(
                onSuccess = { response ->
                    val me = currentUserId.value
                    var hasEncryptedMessages = false
                    var failedToDecrypt = false
                    
                    _messages.value = response.items.map { dto ->
                        // Decrypt message if encrypted
                        val displayText = if (dto.isEncrypted && dto.iv != null) {
                            hasEncryptedMessages = true
                            val decrypted = repository.decryptMessage(dto.content, dto.iv, conversationId)
                            if (decrypted == null) {
                                failedToDecrypt = true
                                "[Không thể giải mã]"
                            } else {
                                decrypted
                            }
                        } else {
                            dto.content
                        }
                        
                        // Resolve sender name from friends map
                        val senderDisplayName = friends[dto.senderId] ?: dto.senderId
                        Message(
                            id = dto.id,
                            text = displayText,
                            timestamp = parseTimestamp(dto.timestamp),
                            isFromMe = dto.senderId == me,
                            senderName = senderDisplayName,
                            senderId = dto.senderId,
                            receiverId = dto.receiverId,
                            conversationId = dto.conversationId,
                            delivered = dto.delivered,
                            seen = dto.seen,
                            clientMessageId = dto.clientMessageId
                        )
                    }
                    
                    // **AUTO-FIX**: If we have encrypted messages but failed to decrypt, 
                    // setup encryption (this handles case where peer created key but we don't have it)
                    // Only attempt once per conversation to avoid infinite loop
                    if (hasEncryptedMessages && failedToDecrypt && !autoSetupAttempted.contains(conversationId)) {
                        android.util.Log.w("ChatViewModel", "Detected encrypted messages but missing key! Auto-setup encryption...")
                        autoSetupAttempted.add(conversationId) // Mark as attempted
                        
                        val contactId = _currentContactId.value
                        if (me != null && contactId != null) {
                            viewModelScope.launch {
                                val participants = listOf(me, contactId)
                                repository.setupConversationEncryption(conversationId, participants).fold(
                                    onSuccess = { success ->
                                        if (success) {
                                            android.util.Log.d("ChatViewModel", "Auto-setup encryption successful! Reloading messages...")
                                            // Reload messages to decrypt them with new key
                                            kotlinx.coroutines.delay(500)
                                            loadMessages(conversationId, limit, cursor)
                                        } else {
                                            android.util.Log.w("ChatViewModel", "Auto-setup returned false")
                                        }
                                    },
                                    onFailure = { error ->
                                        android.util.Log.e("ChatViewModel", "Auto-setup encryption failed: ${error.message}")
                                    }
                                )
                            }
                        }
                    }
                    
                    _messagesLoading.value = false
                    markRead(conversationId = conversationId)
                },
                onFailure = { error ->
                    _messagesLoading.value = false
                    _messagesError.value = error.message
                }
            )
        }
    }

    fun sendMessage(content: String) {
        val to = _currentContactId.value ?: return
        var conversationId = _currentConversationId.value
        viewModelScope.launch {
            val me = currentUserId.value ?: return@launch
            
            // **CRITICAL**: Setup encryption BEFORE sending message to avoid race condition
            if (conversationId != null && !repository.isEncryptionAvailable(conversationId)) {
                try {
                    android.util.Log.d("ChatViewModel", "Setting up E2EE before sending message for conversation: $conversationId")
                    val participants = listOf(me, to)
                    repository.setupConversationEncryption(conversationId, participants).fold(
                        onSuccess = { success ->
                            if (success) {
                                android.util.Log.d("ChatViewModel", "E2EE setup successful before sending")
                            }
                        },
                        onFailure = { error ->
                            android.util.Log.e("ChatViewModel", "E2EE setup failed: ${error.message}")
                        }
                    )
                    // Small delay to ensure keys are stored
                    kotlinx.coroutines.delay(200)
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error setting up encryption before send", e)
                }
            }
            
            val clientMessageId = UUID.randomUUID().toString()
            // Resolve my name from friends map (though it's usually not displayed for own messages)
            val friends = friendsMap.value
            val myDisplayName = friends[me] ?: me
            val optimistic = Message(
                id = "temp_$clientMessageId",
                text = content,
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                senderName = myDisplayName,
                senderId = me,
                receiverId = to,
                clientMessageId = clientMessageId,
                conversationId = conversationId
            )
            _messages.value = _messages.value + optimistic

            repository.sendMessage(to, content, conversationId, clientMessageId).fold(
                onSuccess = {
                    // wait for ACK
                },
                onFailure = { error ->
                    _messages.value = _messages.value.filterNot { it.id == optimistic.id }
                    _messagesError.value = error.message
                }
            )
        }
    }

    fun markRead(fromUserId: String? = null, conversationId: String? = null) {
        viewModelScope.launch { repository.markRead(fromUserId, conversationId) }
    }

    fun sendTyping(isTyping: Boolean) {
        val to = _currentContactId.value ?: return
        viewModelScope.launch { repository.sendTyping(to, isTyping) }
    }

    private fun ensureWebSocketConnected(resumeSince: Long? = null) {
        // Cancel existing connection if any
        webSocketJob?.cancel()
        webSocketJob = viewModelScope.launch {
            try {
                repository.connectWebSocket(resumeSince).collect { handleWebSocketEvent(it) }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "WebSocket connection error", e)
                _webSocketConnected.value = false
                // Try to reconnect after delay
                kotlinx.coroutines.delay(3000)
                if (_currentContactId.value != null) {
                    ensureWebSocketConnected()
                }
            }
        }
    }

    private fun handleWebSocketEvent(event: WebSocketEvent) {
        when (event) {
            is WebSocketEvent.Connected -> {
                _webSocketConnected.value = true
                _messagesError.value = null
            }
            is WebSocketEvent.Closed -> {
                _webSocketConnected.value = false
            }
            is WebSocketEvent.Closing -> {
                _webSocketConnected.value = false
            }
            is WebSocketEvent.Error -> {
                // Don't set error as blocking - just log it, WebSocket might still work
                android.util.Log.e("ChatViewModel", "WebSocket error: ${event.message}")
                // Try to reconnect if connection is lost
                if (!_webSocketConnected.value) {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000)
                        if (_currentContactId.value != null) {
                            ensureWebSocketConnected()
                        }
                    }
                }
            }
            is WebSocketEvent.TypingStarted -> _typingUsers.value = _typingUsers.value + event.userId
            is WebSocketEvent.TypingStopped -> _typingUsers.value = _typingUsers.value - event.userId
            is WebSocketEvent.MessageDelivered -> updateMessageStatus(event.messageId) { it.copy(delivered = true) }
            is WebSocketEvent.MessageSeen -> {
                // Mark the specific message as seen
                updateMessageStatus(event.messageId) { it.copy(seen = true) }
                
                // Also mark all messages from me in the same conversation as seen
                // This handles the case where backend sends seen for one message but we want to mark all as seen
                val conversationId = event.conversationId ?: _currentConversationId.value
                if (conversationId != null) {
                    val updatedMessages = _messages.value.map { msg ->
                        // Mark all my messages in this conversation as seen
                        if (msg.isFromMe && 
                            msg.conversationId == conversationId && 
                            !msg.seen) {
                            msg.copy(seen = true)
                        } else {
                            msg
                        }
                    }
                    _messages.value = updatedMessages
                }
            }
            is WebSocketEvent.MessageAck -> applyAck(event.ack)
            is WebSocketEvent.NewMessage -> handleIncomingMessage(event)
            is WebSocketEvent.RawMessage -> {
                // Try to parse raw message as JSON manually
                android.util.Log.d("ChatViewModel", "Received raw message: ${event.text}")
            }
        }
    }

    private fun updateMessageStatus(messageId: String, transform: (Message) -> Message) {
        _messages.value = _messages.value.map { msg -> if (msg.id == messageId) transform(msg) else msg }
    }

    private fun applyAck(ack: MessageAck) {
        // Update optimistic message id and ensure conversation id is stored
        _messages.value = _messages.value.map { msg ->
            if (msg.clientMessageId == ack.clientMessageId) {
                msg.copy(id = ack.messageId, conversationId = ack.conversationId)
            } else msg
        }
        if (_currentConversationId.value == null) {
            _currentConversationId.value = ack.conversationId
        }
        
        // Clear decrypt cache for this conversation (new message = need to decrypt new preview)
        decryptedConversations.remove(ack.conversationId)
        // Clear auto-setup flag (allow retry if needed)
        autoSetupAttempted.remove(ack.conversationId)
        
        refreshConversations()
    }

    private fun handleIncomingMessage(event: WebSocketEvent.NewMessage) {
        val me = currentUserId.value ?: return
        val ack = event.message.ack
        val conversationId = ack.conversationId
        val isFromMe = event.message.from == me
        
        // Don't process if message is from ourselves (we already have it from optimistic update)
        if (isFromMe) {
            // Just refresh conversations to update last message
            refreshConversations()
            return
        }
        
        // Check if this message is for the current conversation
        val isFromCurrentContact = event.message.from == _currentContactId.value
        val isInCurrentConversation = conversationId.isNotEmpty() && conversationId == _currentConversationId.value
        val isNewConversation = _currentConversationId.value == null && isFromCurrentContact
        val hasCurrentContact = _currentContactId.value != null
        
        // Process message if:
        // 1. It's in the current conversation, OR
        // 2. It's from the current contact (even if conversationId doesn't match yet), OR
        // 3. We have a current contact and this is a new conversation from that contact
        val shouldShowMessage = isInCurrentConversation || isFromCurrentContact || (hasCurrentContact && isNewConversation)
        
        if (shouldShowMessage) {
            // Decrypt message if encrypted
            viewModelScope.launch {
                val displayText = if (event.message.isEncrypted && event.message.iv != null && conversationId.isNotEmpty()) {
                    val decrypted = repository.decryptMessage(event.message.content, event.message.iv, conversationId)
                    decrypted ?: "[Không thể giải mã]"
                } else {
                    event.message.content
                }
                
                // Resolve sender name from friends map
                val friends = friendsMap.value
                val senderDisplayName = friends[event.message.from] ?: event.message.from
                val message = Message(
                    id = ack.messageId.ifEmpty { "temp_${System.currentTimeMillis()}" },
                    text = displayText,
                    timestamp = System.currentTimeMillis(),
                    isFromMe = false,
                    senderName = senderDisplayName,
                    senderId = event.message.from,
                    receiverId = _currentContactId.value ?: me,
                    conversationId = conversationId,
                    clientMessageId = ack.clientMessageId
                )

                // Update conversation ID if we don't have it yet
                if (_currentConversationId.value == null && conversationId.isNotEmpty()) {
                    _currentConversationId.value = conversationId
                }
                
                // Also update contact ID if we don't have it
                if (_currentContactId.value == null) {
                    _currentContactId.value = event.message.from
                }

                // Add message to current chat (check for duplicates)
                val existingMessageIds = _messages.value.map { it.id }.toSet()
                if (message.id !in existingMessageIds && 
                    !_messages.value.any { it.clientMessageId == message.clientMessageId && message.clientMessageId != null }) {
                    _messages.value = _messages.value + message
                }
                
                // Mark as read if in current conversation
                if (conversationId.isNotEmpty()) {
                    markRead(conversationId = conversationId)
                }
            }
        }
        
        // Clear decrypt cache for this conversation (new message = need to decrypt new preview)
        if (conversationId.isNotEmpty()) {
            decryptedConversations.remove(conversationId)
            // Clear auto-setup flag (allow retry if needed)
            autoSetupAttempted.remove(conversationId)
        }
        
        // Always refresh conversations to update last message preview
        refreshConversations()
    }

    fun disconnectWebSocket() {
        viewModelScope.launch {
            // Set offline trước khi disconnect WebSocket
            repository.setOffline()
            // Disconnect WebSocket
            webSocketJob?.cancel()
            webSocketJob = null
            repository.disconnectWebSocket()
            _webSocketConnected.value = false
        }
    }

    private fun parseTimestamp(value: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(value)?.time
                ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun formatTimestamp(value: String?): String {
        if (value.isNullOrBlank()) return ""
        
        return try {
            var date: Date? = null
            val cleanValue = value.trim()
            
            // Thử các format ISO 8601 phổ biến
            val formats = listOf(
                // Format với timezone Z
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                },
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                },
                // Format với timezone +00:00
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()),
                // Format không có timezone
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
                // Fallback: format đơn giản
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            )
            
            for (format in formats) {
                try {
                    date = format.parse(cleanValue)
                    if (date != null) break
                } catch (_: Exception) {
                    continue
                }
            }
            
            // Nếu vẫn không parse được, thử parse thủ công
            if (date == null) {
                try {
                    // Loại bỏ milliseconds và timezone
                    var timePart = cleanValue
                    if (timePart.contains(".")) {
                        timePart = timePart.substringBefore(".")
                    }
                    if (timePart.contains("+")) {
                        timePart = timePart.substringBefore("+")
                    }
                    if (timePart.contains("-") && timePart.length > 10) {
                        // Có timezone dạng -05:00
                        val lastDash = timePart.lastIndexOf("-")
                        if (lastDash > 10) {
                            timePart = timePart.substring(0, lastDash)
                        }
                    }
                    timePart = timePart.replace("Z", "")
                    
                    val simpleFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    simpleFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    date = simpleFormat.parse(timePart)
                } catch (_: Exception) {
                    // Ignore
                }
            }
            
            if (date == null) {
                android.util.Log.w("ChatViewModel", "Could not parse timestamp: $value")
                return ""
            }
            
            val now = System.currentTimeMillis()
            val dateTime = date.time
            val diff = now - dateTime
            
            if (diff < 0) {
                // Timestamp trong tương lai
                return ""
            }
            
            // Tính toán ngày
            val calendarNow = java.util.Calendar.getInstance()
            val calendarDate = java.util.Calendar.getInstance().apply { 
                timeInMillis = dateTime
            }
            
            val isToday = calendarNow.get(java.util.Calendar.YEAR) == calendarDate.get(java.util.Calendar.YEAR) &&
                    calendarNow.get(java.util.Calendar.DAY_OF_YEAR) == calendarDate.get(java.util.Calendar.DAY_OF_YEAR)
            
            val isYesterday = run {
                val yesterday = java.util.Calendar.getInstance().apply {
                    timeInMillis = now
                    add(java.util.Calendar.DAY_OF_YEAR, -1)
                }
                yesterday.get(java.util.Calendar.YEAR) == calendarDate.get(java.util.Calendar.YEAR) &&
                yesterday.get(java.util.Calendar.DAY_OF_YEAR) == calendarDate.get(java.util.Calendar.DAY_OF_YEAR)
            }
            
            // Format output
            when {
                diff < 60_000 -> "Vừa xong"
                diff < 3_600_000 -> {
                    val minutes = (diff / 60_000).toInt()
                    "$minutes phút"
                }
                isToday -> {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    timeFormat.timeZone = java.util.TimeZone.getDefault()
                    timeFormat.format(date)
                }
                isYesterday -> {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    timeFormat.timeZone = java.util.TimeZone.getDefault()
                    "Hôm qua ${timeFormat.format(date)}"
                }
                diff < 7 * 86_400_000 -> {
                    val dayNames = arrayOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")
                    val dayOfWeek = calendarDate.get(java.util.Calendar.DAY_OF_WEEK) - 1
                    val dayName = if (dayOfWeek in dayNames.indices) dayNames[dayOfWeek] else ""
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    timeFormat.timeZone = java.util.TimeZone.getDefault()
                    "$dayName ${timeFormat.format(date)}"
                }
                else -> {
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    dateFormat.timeZone = java.util.TimeZone.getDefault()
                    dateFormat.format(date)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Error formatting timestamp: $value", e)
            ""
        }
    }

    fun deleteConversation(conversationId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId).fold(
                onSuccess = {
                    // Remove conversation from local list
                    _conversations.value = _conversations.value.filter { it.id != conversationId }
                    // Clear current conversation if it's the deleted one
                    if (_currentConversationId.value == conversationId) {
                        _currentConversationId.value = null
                        _currentContactId.value = null
                        _currentContactName.value = null
                        _messages.value = emptyList()
                    }
                    // Refresh conversations list to ensure consistency
                    refreshConversations()
                    onSuccess()
                },
                onFailure = { exception ->
                    onError(exception.message ?: "Không thể xóa cuộc trò chuyện")
                }
            )
        }
    }

    /**
     * Try to decrypt conversation preview asynchronously
     * This fetches the last message and decrypts it to show in conversation list
     * 
     * OPTIMIZATION: Mark conversation as processed to avoid re-decrypt on every refresh
     */
    private fun tryDecryptConversationPreview(conversationId: String) {
        viewModelScope.launch {
            try {
                // Fetch last message of this conversation
                val result = repository.getMessages(conversationId, limit = 1)
                result.fold(
                    onSuccess = { response ->
                        if (response.items.isEmpty()) {
                            // No messages, mark as processed
                            decryptedConversations.add(conversationId)
                            return@fold
                        }
                        
                        val lastMsg = response.items.first()
                        
                        // If message is NOT encrypted, just display it (old message before E2EE)
                        if (!lastMsg.isEncrypted || lastMsg.iv == null) {
                            val me = currentUserId.value
                            val isFromMe = lastMsg.senderId == me
                            val preview = lastMsg.content.take(50)
                            val displayText = if (isFromMe) "Bạn: $preview" else preview
                            
                            _conversations.value = _conversations.value.map { conv ->
                                if (conv.id == conversationId) {
                                    conv.copy(lastMessage = displayText)
                                } else {
                                    conv
                                }
                            }
                            
                            // Mark as processed (no need to check again)
                            decryptedConversations.add(conversationId)
                            return@fold
                        }
                        
                        // Try to decrypt encrypted message
                        val decrypted = repository.decryptMessage(lastMsg.content, lastMsg.iv, conversationId)
                        
                        val me = currentUserId.value
                        val isFromMe = lastMsg.senderId == me
                        
                        if (decrypted != null) {
                            // Decryption successful
                            val preview = decrypted.take(50)
                            val displayText = if (isFromMe) "Bạn: $preview" else preview
                            
                            _conversations.value = _conversations.value.map { conv ->
                                if (conv.id == conversationId) {
                                    conv.copy(lastMessage = displayText)
                                } else {
                                    conv
                                }
                            }
                            
                            // Mark as successfully decrypted
                            decryptedConversations.add(conversationId)
                            android.util.Log.d("ChatViewModel", "Successfully decrypted preview for $conversationId")
                        } else {
                            // Decryption failed (no key available)
                            _conversations.value = _conversations.value.map { conv ->
                                if (conv.id == conversationId) {
                                    conv.copy(lastMessage = "🔒 Tin nhắn")
                                } else {
                                    conv
                                }
                            }
                            
                            // Mark as processed to avoid infinite retries
                            decryptedConversations.add(conversationId)
                            android.util.Log.w("ChatViewModel", "Failed to decrypt preview for $conversationId (no key)")
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("ChatViewModel", "Failed to fetch messages for $conversationId", error)
                        // Keep showing encrypted indicator if fetch fails
                        _conversations.value = _conversations.value.map { conv ->
                            if (conv.id == conversationId) {
                                conv.copy(lastMessage = "🔒 Tin nhắn")
                            } else {
                                conv
                            }
                        }
                        // Mark as processed to avoid retry spam
                        decryptedConversations.add(conversationId)
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error decrypting preview", e)
                // Mark as processed even on error
                decryptedConversations.add(conversationId)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        disconnectWebSocket()
    }
}
