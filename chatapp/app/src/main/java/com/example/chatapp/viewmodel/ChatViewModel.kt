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

    private val currentUserId: StateFlow<String?> = authManager.userId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val myUserId: StateFlow<String?>
        get() = currentUserId

    init {
        viewModelScope.launch {
            loadFriendsMap()
        }
        refreshConversations()
    }

    private suspend fun loadFriendsMap(): Map<String, String> {
        return repository.getFriendsList().fold(
            onSuccess = { response ->
                val map = response.friends.associate { friend ->
                    (friend.id ?: "") to (friend.fullName ?: friend.email ?: "Unknown")
                }
                _friendsMap.value = map
                map
            },
            onFailure = {
                // Return current map if load fails
                friendsMap.value
            }
        )
    }

    fun refreshConversations() {
        viewModelScope.launch {
            // Load friends map first to ensure we have names
            val friends = loadFriendsMap()
            
            _conversationsLoading.value = true
            _conversationsError.value = null
            repository.getConversations().fold(
                onSuccess = { response ->
                    val me = currentUserId.value
                    _conversations.value = response.items.map { dto ->
                        val otherParticipant = dto.participants.firstOrNull { it != me }
                        val displayName = otherParticipant?.let { friends[it] } ?: otherParticipant ?: "Unknown"
                        Conversation(
                            id = dto.id,
                            name = displayName,
                            lastMessage = dto.lastMessagePreview.orEmpty(),
                            lastTime = formatTimestamp(dto.lastMessageAt),
                            unreadCount = dto.unreadCounters[me] ?: 0,
                            isOnline = false,
                            participants = dto.participants,
                            lastMessageAt = dto.lastMessageAt,
                            lastMessagePreview = dto.lastMessagePreview
                        )
                    }
                    _conversationsLoading.value = false
                },
                onFailure = { error ->
                    _conversationsLoading.value = false
                    _conversationsError.value = error.message
                }
            )
        }
    }

    fun openConversation(conversationId: String?, contactId: String, contactName: String?, resumeSince: Long? = null) {
        _currentContactId.value = contactId
        _currentContactName.value = contactName ?: contactId
        _currentConversationId.value = conversationId

        if (conversationId != null) {
            loadMessages(conversationId)
        } else {
            _messages.value = emptyList()
        }

        ensureWebSocketConnected(resumeSince)
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
            repository.getMessages(conversationId, limit, cursor).fold(
                onSuccess = { response ->
                    val me = currentUserId.value
                    _messages.value = response.items.map { dto ->
                        Message(
                            id = dto.id,
                            text = dto.content,
                            timestamp = parseTimestamp(dto.timestamp),
                            isFromMe = dto.senderId == me,
                            senderName = dto.senderId,
                            senderId = dto.senderId,
                            receiverId = dto.receiverId,
                            conversationId = dto.conversationId,
                            delivered = dto.delivered,
                            seen = dto.seen,
                            clientMessageId = dto.clientMessageId
                        )
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
        viewModelScope.launch {
            val clientMessageId = UUID.randomUUID().toString()
            val optimistic = Message(
                id = "temp_$clientMessageId",
                text = content,
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                senderName = currentUserId.value,
                senderId = currentUserId.value,
                receiverId = to,
                clientMessageId = clientMessageId,
                conversationId = _currentConversationId.value
            )
            _messages.value = _messages.value + optimistic

            repository.sendMessage(to, content, clientMessageId).fold(
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
        if (webSocketJob?.isActive == true) return
        webSocketJob = viewModelScope.launch {
            repository.connectWebSocket(resumeSince).collect { handleWebSocketEvent(it) }
        }
    }

    private fun handleWebSocketEvent(event: WebSocketEvent) {
        when (event) {
            is WebSocketEvent.Connected -> _webSocketConnected.value = true
            is WebSocketEvent.Closed -> _webSocketConnected.value = false
            is WebSocketEvent.Closing -> {
                _webSocketConnected.value = false
            }
            is WebSocketEvent.Error -> {
                _messagesError.value = event.message
                _webSocketConnected.value = false
            }
            is WebSocketEvent.TypingStarted -> _typingUsers.value = _typingUsers.value + event.userId
            is WebSocketEvent.TypingStopped -> _typingUsers.value = _typingUsers.value - event.userId
            is WebSocketEvent.MessageDelivered -> updateMessageStatus(event.messageId) { it.copy(delivered = true) }
            is WebSocketEvent.MessageSeen -> updateMessageStatus(event.messageId) { it.copy(seen = true) }
            is WebSocketEvent.MessageAck -> applyAck(event.ack)
            is WebSocketEvent.NewMessage -> handleIncomingMessage(event)
            is WebSocketEvent.RawMessage -> { /* ignore */ }
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
        refreshConversations()
    }

    private fun handleIncomingMessage(event: WebSocketEvent.NewMessage) {
        val me = currentUserId.value
        val conversationId = event.message.ack.conversationId
        val message = Message(
            id = event.message.ack.messageId,
            text = event.message.content,
            timestamp = System.currentTimeMillis(),
            isFromMe = event.message.from == me,
            senderName = event.message.from,
            senderId = event.message.from,
            receiverId = _currentContactId.value,
            conversationId = conversationId,
            clientMessageId = event.message.ack.clientMessageId
        )

        if (_currentConversationId.value == null && event.message.from == _currentContactId.value) {
            _currentConversationId.value = conversationId
        }

        if (conversationId == _currentConversationId.value) {
            _messages.value = _messages.value + message
            markRead(conversationId = conversationId)
        }
        refreshConversations()
    }

    fun disconnectWebSocket() {
        webSocketJob?.cancel()
        webSocketJob = null
        repository.disconnectWebSocket()
        _webSocketConnected.value = false
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
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(value) ?: return ""
            val now = System.currentTimeMillis()
            val diff = now - date.time
            when {
                diff < 60_000 -> "Vừa xong"
                diff < 3_600_000 -> "${diff / 60_000} phút"
                diff < 86_400_000 -> "${diff / 3_600_000} giờ"
                else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) {
            ""
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectWebSocket()
    }
}
