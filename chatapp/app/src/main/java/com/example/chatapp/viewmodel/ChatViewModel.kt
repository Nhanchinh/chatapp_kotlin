package com.example.chatapp.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.model.Conversation
import com.example.chatapp.data.model.MediaItem
import com.example.chatapp.data.model.Message
import com.example.chatapp.data.model.MediaStatus
import com.example.chatapp.data.repository.ChatRepository
import com.example.chatapp.data.remote.WebSocketEvent
import com.example.chatapp.data.remote.model.MessageAck
import com.example.chatapp.data.remote.model.UserDto
import com.example.chatapp.data.remote.model.GroupInfoResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val _currentConversationIsGroup = MutableStateFlow(false)
    val currentConversationIsGroup: StateFlow<Boolean> = _currentConversationIsGroup.asStateFlow()

    private val _currentContactId = MutableStateFlow<String?>(null)
    val currentContactId: StateFlow<String?> = _currentContactId.asStateFlow()
    private val _currentContactName = MutableStateFlow<String?>(null)
    val currentContactName: StateFlow<String?> = _currentContactName.asStateFlow()


    private val _typingUsers = MutableStateFlow<Set<String>>(emptySet())
    val typingUsers: StateFlow<Set<String>> = _typingUsers.asStateFlow()

    private val _webSocketConnected = MutableStateFlow(false)
    val webSocketConnected: StateFlow<Boolean> = _webSocketConnected.asStateFlow()

    private val _mediaGallery = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaGallery: StateFlow<List<MediaItem>> = _mediaGallery.asStateFlow()

    private val _mediaGalleryLoading = MutableStateFlow(false)
    val mediaGalleryLoading: StateFlow<Boolean> = _mediaGalleryLoading.asStateFlow()

    private val _mediaGalleryError = MutableStateFlow<String?>(null)
    val mediaGalleryError: StateFlow<String?> = _mediaGalleryError.asStateFlow()

    private var webSocketJob: Job? = null

    // E2EE: Cache conversations đã decrypt thành công (tránh decrypt lại mỗi lần refresh)
    private val decryptedConversations = mutableSetOf<String>()
    
    // E2EE: Track conversations đã try auto-setup (tránh infinite loop)
    private val autoSetupAttempted = mutableSetOf<String>()

    // Track media downloads to avoid duplicate requests
    private val downloadingMediaIds = mutableSetOf<String>()

    private val currentUserId: StateFlow<String?> = authManager.userId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val myUserId: StateFlow<String?>
        get() = currentUserId

    fun getMyUserIdValue(): String? = currentUserId.value

    suspend fun getZegoCallToken(callId: String, expirySeconds: Int = 3600): Result<com.example.chatapp.data.remote.model.ZegoTokenResponse> {
        return repository.getZegoToken(callId, expirySeconds)
    }

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
                            // First, create conversations with initial preview from backend
                            val initialConversations = response.items.mapNotNull { dto ->
                                try {
                                    val otherParticipant = dto.participants.firstOrNull { it != me }
                                    val displayName = otherParticipant?.let { friends[it] } ?: otherParticipant ?: "Unknown"
                                    val formattedTime = try {
                                        formatTimestamp(dto.lastMessageAt)
                                    } catch (e: Exception) {
                                        android.util.Log.e("ChatViewModel", "Error formatting time for conversation ${dto.id}", e)
                                        ""
                                    }
                                    
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
                                        name = dto.name ?: displayName,
                                        lastMessage = displayMessage,
                                        lastTime = formattedTime,
                                        unreadCount = dto.unreadCounters[me] ?: 0,
                                        isOnline = dto.isOnline ?: false,
                                        participants = dto.participants,
                                        lastMessageAt = dto.lastMessageAt,
                                        lastMessagePreview = dto.lastMessagePreview,
                                        lastMessageSenderId = dto.lastMessageSenderId,
                                        isGroup = dto.isGroup ?: false,
                                        groupKeyVersion = dto.groupKeyVersion,
                                        ownerId = dto.ownerId
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatViewModel", "Error processing conversation ${dto.id}", e)
                                    null
                                }
                            }
                            
                            // Display conversations first (fast initial render)
                            _conversations.value = initialConversations
                            _conversationsLoading.value = false
                            
                            // Check deleted status asynchronously for conversations with non-encrypted previews
                            // Only check top 20 conversations to avoid performance issues
                            // The rest will be checked when user scrolls or when WebSocket event arrives
                            val conversationsToCheck = initialConversations
                                .take(20) // Limit to top 20 for performance
                                .filter { conv ->
                                    val preview = conv.lastMessagePreview.orEmpty()
                                    preview.isNotEmpty() && 
                                    preview != "[Encrypted Message]" && 
                                    preview != "[Media]" &&
                                    !preview.startsWith("🔒") &&
                                    !preview.contains("Tin nhắn đã bị thu hồi") // Skip if already marked as deleted
                                }
                            
                            // Fetch last messages in parallel (only for top conversations)
                            // Use viewModelScope.launch since we're in a callback, not directly in coroutine scope
                            if (conversationsToCheck.isNotEmpty()) {
                                viewModelScope.launch {
                                    try {
                                        val deletedStatusMap: Map<String, Boolean> = coroutineScope {
                                            conversationsToCheck.map { conv ->
                                                async {
                                                    val result = repository.getMessages(conv.id, limit = 1)
                                                    val isDeleted = result.fold(
                                                        onSuccess = { msgResponse ->
                                                            msgResponse.items.isNotEmpty() && msgResponse.items.first().deleted
                                                        },
                                                        onFailure = { _ -> false }
                                                    )
                                                    conv.id to isDeleted
                                                }
                                            }.awaitAll().toMap()
                                        }
                                        
                                        // Update conversations with deleted status (only if changed)
                                        _conversations.value = _conversations.value.map { conv ->
                                            if (deletedStatusMap[conv.id] == true) {
                                                val isFromMe = conv.lastMessageSenderId == me
                                                val deletedPreview = if (isFromMe) "Bạn: Tin nhắn đã bị thu hồi" else "Tin nhắn đã bị thu hồi"
                                                conv.copy(lastMessage = deletedPreview)
                                            } else {
                                                conv
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("ChatViewModel", "Error checking deleted status", e)
                                        // Ignore errors, keep original preview
                                    }
                                }
                            }
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
        _currentConversationIsGroup.value = false

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

    fun openGroup(conversationId: String, groupName: String?) {
        _currentConversationId.value = conversationId
        _currentContactId.value = null
        _currentContactName.value = groupName
        _currentConversationIsGroup.value = true
        viewModelScope.launch {
            loadMessages(conversationId)
            ensureWebSocketConnected()
        }
    }

    fun createGroup(
        name: String,
        memberIds: List<String>,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createGroupEncrypted(name.ifBlank { "Nhóm mới" }, memberIds).fold(
                onSuccess = { convoId ->
                    refreshConversations()
                    onSuccess(convoId)
                },
                onFailure = { error ->
                    _messagesError.value = error.message
                    onError(error.message ?: "Tạo nhóm thất bại")
                }
            )
        }
    }

    fun fetchGroupInfo(conversationId: String, onLoaded: (GroupInfoResponse) -> Unit = {}) {
        viewModelScope.launch {
            repository.getGroupInfo(conversationId).fold(
                onSuccess = { resp -> onLoaded(resp) },
                onFailure = { error -> _messagesError.value = error.message }
            )
        }
    }

    fun addGroupMembers(conversationId: String, memberIds: List<String>, onLoaded: (GroupInfoResponse) -> Unit = {}) {
        viewModelScope.launch {
            repository.addGroupMembers(conversationId, memberIds).fold(
                onSuccess = { resp -> onLoaded(resp) },
                onFailure = { error -> _messagesError.value = error.message }
            )
        }
    }

    fun addGroupMembers(
        conversationId: String,
        memberIds: List<String>,
        onSuccess: (GroupInfoResponse) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.addGroupMembers(conversationId, memberIds).fold(
                onSuccess = { resp -> onSuccess(resp) },
                onFailure = { error -> onError(error.message ?: "Thêm thành viên thất bại") }
            )
        }
    }

    fun removeGroupMember(conversationId: String, memberId: String, onLoaded: (GroupInfoResponse) -> Unit = {}) {
        viewModelScope.launch {
            repository.removeGroupMember(conversationId, memberId).fold(
                onSuccess = { resp -> onLoaded(resp) },
                onFailure = { error -> _messagesError.value = error.message }
            )
        }
    }

    fun leaveGroup(conversationId: String, onLoaded: (GroupInfoResponse) -> Unit = {}) {
        viewModelScope.launch {
            repository.leaveGroup(conversationId).fold(
                onSuccess = { resp -> onLoaded(resp) },
                onFailure = { error -> _messagesError.value = error.message }
            )
        }
    }

    private fun findAndLoadExistingConversation(contactId: String) {
        viewModelScope.launch {
            val me = currentUserId.value ?: return@launch
            
            // First, try to find in already loaded conversations
            // **CRITICAL**: Only find 1-1 conversations (not groups)
            var existingConversation = conversations.value.firstOrNull { conversation ->
                val participants = conversation.participants.toSet()
                participants.contains(me) && participants.contains(contactId) && !conversation.isGroup
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
                            name = dto.name ?: displayName,
                            lastMessage = dto.lastMessagePreview.orEmpty(),
                            lastTime = formatTimestamp(dto.lastMessageAt),
                            unreadCount = dto.unreadCounters[me] ?: 0,
                            isOnline = dto.isOnline ?: false,
                            participants = dto.participants,
                            lastMessageAt = dto.lastMessageAt,
                            lastMessagePreview = dto.lastMessagePreview,
                            lastMessageSenderId = dto.lastMessageSenderId,
                            isGroup = dto.isGroup ?: false,  // **CRITICAL**: Include isGroup to filter groups
                            groupKeyVersion = dto.groupKeyVersion,
                            ownerId = dto.ownerId
                        )
                    }
                    _conversations.value = updatedConversations
                    _conversationsLoading.value = false
                    
                    // Try to find again after refresh
                    // **CRITICAL**: Only find 1-1 conversations (not groups)
                    existingConversation = updatedConversations.firstOrNull { conversation ->
                        val participants = conversation.participants.toSet()
                        participants.contains(me) && participants.contains(contactId) && !conversation.isGroup
                    }
                    
                    if (existingConversation != null) {
                        // Found existing conversation after refresh
                        _currentConversationId.value = existingConversation.id
                        loadMessages(existingConversation.id)
                    } else {
                        // No existing conversation found - create new conversation with encryption
                        createNewConversation(contactId)
                    }
                },
                onFailure = { error ->
                    _conversationsLoading.value = false
                    // Even if refresh fails, still allow user to start new conversation
                    // Try to create new conversation
                    createNewConversation(contactId)
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
        clearMediaGallery()
    }
    
    /**
     * Create a new conversation with encryption keys.
     * This follows the new flow:
     * 1. Prepare encrypted keys
     * 2. Call createConversation API (server creates conversation + stores keys)
     * 3. Store session key locally
     * 4. Ready to send messages
     */
    private fun createNewConversation(contactId: String) {
        viewModelScope.launch {
            _conversationsLoading.value = true
            _messagesLoading.value = true
            
            repository.createConversation(contactId).fold(
                onSuccess = { conversationId ->
                    android.util.Log.d("ChatViewModel", "Successfully created conversation: $conversationId")
                    _currentConversationId.value = conversationId
                    _messages.value = emptyList()
                    _conversationsLoading.value = false
                    _messagesLoading.value = false
                    // Conversation is ready, user can now send messages
                },
                onFailure = { error ->
                    android.util.Log.e("ChatViewModel", "Failed to create conversation", error)
                    _conversationsLoading.value = false
                    _messagesLoading.value = false
                    _messagesError.value = "Failed to create conversation: ${error.message}"
                    // Still allow user to try sending message (fallback to old flow)
                    _currentConversationId.value = null
                    _messages.value = emptyList()
                }
            )
        }
    }

    fun loadMediaGallery(conversationId: String) {
        viewModelScope.launch {
            _mediaGalleryLoading.value = true
            _mediaGalleryError.value = null
            repository.getMediaGallery(conversationId).fold(
                onSuccess = { items ->
                    _mediaGallery.value = items
                    _mediaGalleryLoading.value = false
                },
                onFailure = { error ->
                    _mediaGalleryLoading.value = false
                    _mediaGalleryError.value = error.message
                }
            )
        }
    }

    fun clearMediaGallery() {
        _mediaGallery.value = emptyList()
        _mediaGalleryError.value = null
        _mediaGalleryLoading.value = false
    }

    suspend fun ensureMediaCached(mediaId: String, conversationId: String): String? {
        return repository.ensureMediaCached(mediaId, conversationId).getOrNull()
    }

    fun downloadAndOpenFile(mediaId: String, conversationId: String, mimeType: String?) {
        viewModelScope.launch {
            val cachedPath = ensureMediaCached(mediaId, conversationId)
            if (cachedPath != null) {
                // File already cached, save to Downloads and open
                val context = getApplication<android.app.Application>()
                val fileName = cachedPath.substringAfterLast("/")
                val success = com.example.chatapp.ui.common.saveFileToDownloads(
                    context,
                    cachedPath,
                    mimeType,
                    fileName
                )
                if (!success) {
                    android.util.Log.e("ChatViewModel", "Failed to save file to Downloads")
                    _messagesError.value = "Không thể lưu file"
                }
            } else {
                _messagesError.value = "Không thể tải file"
            }
        }
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
                    
                    val mappedMessages = response.items.map { dto ->
                        // Decrypt message if encrypted
                        val isMediaMessage = dto.mediaId != null
                        val isVoice = dto.mediaMimeType?.startsWith("audio/") == true
                        val baseText = if (isMediaMessage) {
                            if (dto.senderId == me) {
                                if (isVoice) "[Bạn đã gửi một voice]" else "[Bạn đã gửi một ảnh]"
                            } else {
                                if (isVoice) "[Đã gửi một voice]" else "[Đã gửi một ảnh]"
                            }
                        } else {
                            dto.content
                        }

                        // Check if message is deleted
                        val displayText = if (dto.deleted) {
                            "Tin nhắn đã bị thu hồi"
                        } else if (dto.isEncrypted && dto.iv != null) {
                            hasEncryptedMessages = true
                            var decrypted = repository.decryptMessage(dto.content, dto.iv, conversationId)
                            
                            // If decryption failed, mark for auto-setup (will retry after setup)
                            if (decrypted == null) {
                                failedToDecrypt = true
                                "[Không thể giải mã]"
                            } else {
                                decrypted
                            }
                        } else {
                            baseText
                        }
                        
                        // Resolve sender name from friends map
                        val senderDisplayName = friends[dto.senderId] ?: dto.senderId
                        val mediaStatus = when {
                            dto.mediaId == null -> MediaStatus.NONE
                            dto.senderId == me -> MediaStatus.READY
                            else -> MediaStatus.DOWNLOADING
                        }
                        val localMediaPath = dto.mediaId?.let { repository.getCachedMediaPath(it) }
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
                            clientMessageId = dto.clientMessageId,
                            mediaId = dto.mediaId,
                            mediaMimeType = dto.mediaMimeType,
                            mediaSize = dto.mediaSize,
                            mediaLocalPath = localMediaPath,
                            mediaStatus = mediaStatus,
                            mediaDuration = dto.mediaDuration,
                            deleted = dto.deleted,
                            replyTo = dto.replyTo,
                            reactions = dto.reactions
                        )
                    }
                    _messages.value = mappedMessages
                    scheduleMediaDownloads(mappedMessages)
                    
                    // **AUTO-FIX**: If we have encrypted messages but failed to decrypt, 
                    // setup encryption (this handles case where peer created key but we don't have it)
                    // Only attempt once per conversation to avoid infinite loop
                    if (hasEncryptedMessages && failedToDecrypt && !autoSetupAttempted.contains(conversationId)) {
                        android.util.Log.w("ChatViewModel", "Detected encrypted messages but missing key! Auto-setup encryption...")
                        android.util.Log.w("ChatViewModel", "This may be due to outdated key (encrypted with old public key)")
                        autoSetupAttempted.add(conversationId) // Mark as attempted
                        
                        val contactId = _currentContactId.value
                        if (me != null && contactId != null) {
                            viewModelScope.launch {
                                // First, try to delete outdated key (if any)
                                android.util.Log.d("ChatViewModel", "Attempting to delete outdated conversation key...")
                                repository.deleteOutdatedConversationKey(conversationId).fold(
                                    onSuccess = { deleted ->
                                        if (deleted) {
                                            android.util.Log.d("ChatViewModel", "✅ Deleted outdated key, now will setup new encryption")
                                        } else {
                                            android.util.Log.w("ChatViewModel", "Could not delete outdated key (may not exist)")
                                        }
                                    },
                                    onFailure = { error ->
                                        android.util.Log.w("ChatViewModel", "Error deleting outdated key: ${error.message}")
                                    }
                                )
                                
                                // Now setup encryption (will fetch existing key or create new one)
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
                                            android.util.Log.w("ChatViewModel", "SOLUTION: Delete this conversation and create a new one")
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

    fun sendMessage(content: String, replyTo: String? = null) {
        if (_currentConversationIsGroup.value) {
            sendGroupMessageInternal(content, replyTo)
            return
        }
        val to = _currentContactId.value ?: return
        var conversationId = _currentConversationId.value
        viewModelScope.launch {
            val me = currentUserId.value ?: return@launch
            
            // If no conversation ID, create one first (new flow)
            if (conversationId == null) {
                android.util.Log.d("ChatViewModel", "No conversation ID, creating new conversation...")
                repository.createConversation(to).fold(
                    onSuccess = { newConversationId ->
                        conversationId = newConversationId
                        _currentConversationId.value = newConversationId
                        android.util.Log.d("ChatViewModel", "Created conversation: $newConversationId")
                    },
                    onFailure = { error ->
                        android.util.Log.e("ChatViewModel", "Failed to create conversation: ${error.message}")
                        _messagesError.value = "Failed to create conversation: ${error.message}"
                        return@launch
                    }
                )
            }
            
            // At this point, conversationId should be set and encryption keys should be ready
            if (conversationId == null) {
                android.util.Log.e("ChatViewModel", "Conversation ID is still null, cannot send message")
                return@launch
            }
            
            val clientMessageId = UUID.randomUUID().toString()
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
                conversationId = conversationId,
                replyTo = replyTo
            )
            _messages.value = _messages.value + optimistic

            repository.sendMessage(to, content, conversationId, clientMessageId, replyTo = replyTo).fold(
                onSuccess = { },
                onFailure = { error ->
                    _messages.value = _messages.value.filterNot { it.id == optimistic.id }
                    _messagesError.value = error.message
                }
            )
        }
    }

    private fun sendGroupMessageInternal(content: String, replyTo: String?) {
        val conversationId = _currentConversationId.value ?: return
        viewModelScope.launch {
            val me = currentUserId.value ?: return@launch
            val clientMessageId = UUID.randomUUID().toString()
            val myDisplayName = friendsMap.value[me] ?: me
            val optimistic = Message(
                id = "temp_$clientMessageId",
                text = content,
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                senderName = myDisplayName,
                senderId = me,
                receiverId = null,
                clientMessageId = clientMessageId,
                conversationId = conversationId,
                replyTo = replyTo
            )
            _messages.value = _messages.value + optimistic

            repository.sendGroupMessage(
                conversationId = conversationId,
                content = content,
                clientMessageId = clientMessageId,
                replyTo = replyTo
            ).fold(
                onSuccess = { },
                onFailure = { error ->
                    _messages.value = _messages.value.filterNot { it.id == optimistic.id }
                    _messagesError.value = error.message
                }
            )
        }
    }

    fun reactToMessage(messageId: String, emoji: String) {
        viewModelScope.launch {
            repository.reactToMessage(messageId, emoji).fold(
                onSuccess = { response ->
                    // Update local message reactions optimistically
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == messageId) {
                            msg.copy(reactions = response.reactions)
                        } else {
                            msg
                        }
                    }
                },
                onFailure = { error ->
                    _messagesError.value = "Failed to react: ${error.message}"
                }
            )
        }
    }

    fun sendImage(uri: Uri) {
        sendMedia(uri, "[Đang gửi ảnh]", "[Đã gửi ảnh]", "[Gửi ảnh thất bại]")
    }

    fun sendVideo(uri: Uri) {
        sendMedia(uri, "[Đang gửi video]", "[Đã gửi video]", "[Gửi video thất bại]")
    }

    fun sendFile(uri: Uri) {
        sendMedia(uri, "[Đang gửi file]", "[Đã gửi file]", "[Gửi file thất bại]")
    }

    fun sendVoice(uri: Uri, durationSec: Double) {
        if (_currentConversationIsGroup.value) {
            sendGroupVoice(uri, durationSec)
            return
        }
        val to = _currentContactId.value ?: return
        var conversationId = _currentConversationId.value
        viewModelScope.launch {
            val me = currentUserId.value ?: return@launch

            if (conversationId == null) {
                android.util.Log.d("ChatViewModel", "No conversation ID, creating new conversation (voice)...")
                repository.createConversation(to).fold(
                    onSuccess = { newConversationId ->
                        conversationId = newConversationId
                        _currentConversationId.value = newConversationId
                        android.util.Log.d("ChatViewModel", "Created conversation: $newConversationId")
                    },
                    onFailure = { error ->
                        android.util.Log.e("ChatViewModel", "Failed to create conversation: ${error.message}")
                        _messagesError.value = "Failed to create conversation: ${error.message}"
                        return@launch
                    }
                )
            }

            if (conversationId == null) {
                android.util.Log.e("ChatViewModel", "Conversation ID is still null, cannot send voice")
                return@launch
            }

            val clientMessageId = UUID.randomUUID().toString()
            val friends = friendsMap.value
            val myDisplayName = friends[me] ?: me
            val optimistic = Message(
                id = "temp_media_$clientMessageId",
                text = "[Đang ghi âm...]",
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                senderName = myDisplayName,
                senderId = me,
                receiverId = to,
                clientMessageId = clientMessageId,
                conversationId = conversationId,
                mediaLocalPath = uri.toString(),
                mediaStatus = MediaStatus.UPLOADING
            )
            _messages.value = _messages.value + optimistic

            repository.sendVoiceMessage(
                to = to,
                conversationId = conversationId!!,
                clientMessageId = clientMessageId,
                mediaUri = uri,
                mediaDurationSec = durationSec
            ).fold(
                onSuccess = { mediaResult ->
                    updateMessageByClientMessageId(clientMessageId) { msg ->
                        msg.copy(
                            mediaId = mediaResult.mediaId,
                            mediaMimeType = mediaResult.mimeType,
                            mediaSize = mediaResult.size,
                            mediaLocalPath = mediaResult.localPath,
                            mediaStatus = MediaStatus.READY,
                            text = "[Đã gửi voice]",
                            mediaDuration = durationSec
                        )
                    }
                },
                onFailure = { error ->
                    updateMessageByClientMessageId(clientMessageId) { msg ->
                        msg.copy(
                            mediaStatus = MediaStatus.FAILED,
                            text = "[Gửi voice thất bại]"
                        )
                    }
                    _messagesError.value = error.message
                }
            )
        }
    }

    private fun sendGroupVoice(uri: Uri, durationSec: Double) {
        val conversationId = _currentConversationId.value ?: return
        viewModelScope.launch {
            val me = currentUserId.value ?: return@launch
            val clientMessageId = UUID.randomUUID().toString()
            val myDisplayName = friendsMap.value[me] ?: me
            val optimistic = Message(
                id = "temp_media_$clientMessageId",
                text = "[Đang ghi âm...]",
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                senderName = myDisplayName,
                senderId = me,
                receiverId = null,
                clientMessageId = clientMessageId,
                conversationId = conversationId,
                mediaLocalPath = uri.toString(),
                mediaStatus = MediaStatus.UPLOADING,
                mediaDuration = durationSec
            )
            _messages.value = _messages.value + optimistic

            repository.sendVoiceMessage(
                to = null, // not used in group
                conversationId = conversationId,
                clientMessageId = clientMessageId,
                mediaUri = uri,
                mediaDurationSec = durationSec
            ).fold(
                onSuccess = { mediaResult ->
                    updateMessageByClientMessageId(clientMessageId) { msg ->
                        msg.copy(
                            mediaId = mediaResult.mediaId,
                            mediaMimeType = mediaResult.mimeType,
                            mediaSize = mediaResult.size,
                            mediaLocalPath = mediaResult.localPath,
                            mediaStatus = MediaStatus.READY,
                            text = "[Đã gửi voice]",
                            mediaDuration = durationSec
                        )
                    }
                },
                onFailure = { error ->
                    updateMessageByClientMessageId(clientMessageId) { msg ->
                        msg.copy(
                            mediaStatus = MediaStatus.FAILED,
                            text = "[Gửi voice thất bại]"
                        )
                    }
                    _messagesError.value = error.message
                }
            )
        }
    }

    private fun sendMedia(
        uri: Uri,
        uploadingText: String,
        successText: String,
        failedText: String,
        mediaDurationSec: Double? = null,
        contentPlaceholder: String = "[Media]"
    ) {
        val to = _currentContactId.value ?: return
        var conversationId = _currentConversationId.value
        viewModelScope.launch {
            val me = currentUserId.value ?: return@launch

            if (conversationId == null) {
                android.util.Log.d("ChatViewModel", "No conversation ID, creating new conversation (media)...")
                repository.createConversation(to).fold(
                    onSuccess = { newConversationId ->
                        conversationId = newConversationId
                        _currentConversationId.value = newConversationId
                        android.util.Log.d("ChatViewModel", "Created conversation: $newConversationId")
                    },
                    onFailure = { error ->
                        android.util.Log.e("ChatViewModel", "Failed to create conversation: ${error.message}")
                        _messagesError.value = "Failed to create conversation: ${error.message}"
                        return@launch
                    }
                )
            }

            if (conversationId == null) {
                android.util.Log.e("ChatViewModel", "Conversation ID is still null, cannot send media")
                return@launch
            }

            val clientMessageId = UUID.randomUUID().toString()
            val friends = friendsMap.value
            val myDisplayName = friends[me] ?: me
            val optimistic = Message(
                id = "temp_media_$clientMessageId",
                text = uploadingText,
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                senderName = myDisplayName,
                senderId = me,
                receiverId = to,
                clientMessageId = clientMessageId,
                conversationId = conversationId,
                mediaLocalPath = uri.toString(),
                mediaStatus = MediaStatus.UPLOADING
            )
            _messages.value = _messages.value + optimistic

            repository.sendMediaMessage(
                to = to,
                conversationId = conversationId!!,
                clientMessageId = clientMessageId,
                mediaUri = uri,
                mediaDurationSec = mediaDurationSec,
                contentPlaceholder = contentPlaceholder
            ).fold(
                onSuccess = { mediaResult ->
                    updateMessageByClientMessageId(clientMessageId) { msg ->
                        msg.copy(
                            mediaId = mediaResult.mediaId,
                            mediaMimeType = mediaResult.mimeType,
                            mediaSize = mediaResult.size,
                            mediaLocalPath = mediaResult.localPath,
                            mediaStatus = MediaStatus.READY,
                            text = successText
                        )
                    }
                },
                onFailure = { error ->
                    updateMessageByClientMessageId(clientMessageId) { msg ->
                        msg.copy(
                            mediaStatus = MediaStatus.FAILED,
                            text = failedText
                        )
                    }
                    _messagesError.value = error.message
                }
            )
        }
    }

    fun retryDownloadMedia(messageId: String) {
        val message = _messages.value.firstOrNull { it.id == messageId }
        if (message?.mediaId != null && !message.conversationId.isNullOrBlank()) {
            downloadMediaForMessage(message.id, message.mediaId, message.conversationId!!)
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
            is WebSocketEvent.MessageDeleted -> {
                // Update message to show as deleted
                val conversationId = event.conversationId ?: _currentConversationId.value
                if (conversationId != null) {
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == event.messageId) {
                            msg.copy(deleted = true, text = "Tin nhắn đã bị thu hồi")
                        } else {
                            msg
                        }
                    }
                    // Update conversation preview immediately
                    // Check if deleted message is the last message by comparing timestamps
                    val deletedMessage = _messages.value.find { it.id == event.messageId }
                    val lastMessage = _messages.value.maxByOrNull { it.timestamp }
                    val isLastMessage = deletedMessage != null && lastMessage != null && 
                                       deletedMessage.timestamp >= lastMessage.timestamp
                    
                    if (isLastMessage) {
                        val me = currentUserId.value
                        val isFromMe = deletedMessage?.isFromMe ?: false
                        val displayText = if (isFromMe) "Bạn: Tin nhắn đã bị thu hồi" else "Tin nhắn đã bị thu hồi"
                        
                        _conversations.value = _conversations.value.map { conv ->
                            if (conv.id == conversationId) {
                                conv.copy(lastMessage = displayText)
                            } else {
                                conv
                            }
                        }
                    }
                    // Also refresh conversations to get updated preview from server
                    refreshConversations()
                }
            }
            is WebSocketEvent.MessageAck -> applyAck(event.ack)
            is WebSocketEvent.NewMessage -> handleIncomingMessage(event)
            is WebSocketEvent.Reaction -> {
                // Update message reactions
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == event.messageId) {
                        msg.copy(reactions = event.reactions)
                    } else {
                        msg
                    }
                }
            }
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
                var updated = msg.copy(id = ack.messageId, conversationId = ack.conversationId)
                if (ack.mediaId != null) {
                    updated = updated.copy(
                        mediaId = ack.mediaId,
                        mediaMimeType = ack.mediaMimeType,
                        mediaSize = ack.mediaSize,
                        mediaDuration = ack.mediaDuration ?: updated.mediaDuration,
                        mediaStatus = if (updated.mediaStatus == MediaStatus.UPLOADING) MediaStatus.READY else updated.mediaStatus
                    )
                }
                updated
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
                val isMediaMessage = event.message.mediaId != null
                val isVoice = event.message.mediaMimeType?.startsWith("audio/") == true
                val baseText = when {
                    isMediaMessage && isVoice && isFromMe -> "[Bạn đã gửi một voice]"
                    isMediaMessage && isVoice -> "[Đã nhận một voice]"
                    isMediaMessage && isFromMe -> "[Bạn đã gửi một ảnh]"
                    isMediaMessage -> "[Đã nhận một ảnh]"
                    else -> event.message.content
                } ?: ""

                val displayText = if (event.message.isEncrypted && event.message.iv != null && conversationId.isNotEmpty()) {
                    // Try to decrypt - if fails, try to fetch key from server and retry
                    var decrypted = repository.decryptMessage(event.message.content, event.message.iv, conversationId)
                    
                    // If decryption failed, try to fetch key from server (peer may have created conversation)
                    if (decrypted == null) {
                        android.util.Log.d("ChatViewModel", "Decryption failed, trying to fetch key from server for conversation $conversationId")
                        // Try to setup encryption (will fetch key from server if exists)
                        val participants = listOf(me, event.message.from)
                        repository.setupConversationEncryption(conversationId, participants).fold(
                            onSuccess = {
                                // Retry decryption after fetching key
                                decrypted = repository.decryptMessage(event.message.content, event.message.iv, conversationId)
                            },
                            onFailure = { error ->
                                android.util.Log.e("ChatViewModel", "Failed to setup encryption for conversation $conversationId", error)
                            }
                        )
                    }
                    
                    decrypted ?: "[Không thể giải mã]"
                } else {
                    baseText
                }
                
                // Resolve sender name from friends map
                val friends = friendsMap.value
                val senderDisplayName = friends[event.message.from] ?: event.message.from
                val mediaStatus = when {
                    event.message.mediaId == null -> MediaStatus.NONE
                    isFromMe -> MediaStatus.READY
                    else -> MediaStatus.DOWNLOADING
                }
                val message = Message(
                    id = ack.messageId.ifEmpty { "temp_${System.currentTimeMillis()}" },
                    text = displayText,
                    timestamp = System.currentTimeMillis(),
                    isFromMe = false,
                    senderName = senderDisplayName,
                    senderId = event.message.from,
                    receiverId = _currentContactId.value ?: me,
                    conversationId = conversationId,
                    clientMessageId = ack.clientMessageId,
                    mediaId = event.message.mediaId,
                    mediaMimeType = event.message.mediaMimeType,
                    mediaSize = event.message.mediaSize,
                    mediaDuration = event.message.mediaDuration,
                    mediaStatus = mediaStatus,
                    deleted = false,  // WebSocket messages are new, so not deleted
                    replyTo = event.message.replyTo,
                    reactions = null  // Reactions will be updated via WebSocket events
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

                if (event.message.mediaId != null && conversationId.isNotEmpty()) {
                    downloadMediaForMessage(message.id, event.message.mediaId, conversationId)
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

    fun deleteMessage(messageId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            repository.deleteMessage(messageId).fold(
                onSuccess = {
                    // Update message in local list to mark as deleted
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == messageId) {
                            msg.copy(deleted = true, text = "Tin nhắn đã bị thu hồi")
                        } else {
                            msg
                        }
                    }
                    onSuccess()
                },
                onFailure = { exception ->
                    onError(exception.message ?: "Không thể xóa tin nhắn")
                }
            )
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
                        
                        // Check if message is deleted
                        if (lastMsg.deleted) {
                            _conversations.value = _conversations.value.map { conv ->
                                if (conv.id == conversationId) {
                                    val me = currentUserId.value
                                    val isFromMe = lastMsg.senderId == me
                                    val displayText = if (isFromMe) "Bạn: Tin nhắn đã bị thu hồi" else "Tin nhắn đã bị thu hồi"
                                    conv.copy(lastMessage = displayText)
                                } else {
                                    conv
                                }
                            }
                            decryptedConversations.add(conversationId)
                            return@fold
                        }
                        
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

    /**
     * Check if the last message of a conversation is deleted
     * This is used for non-encrypted messages to update preview
     */
    private fun checkLastMessageDeletedStatus(conversationId: String) {
        viewModelScope.launch {
            try {
                val result = repository.getMessages(conversationId, limit = 1)
                result.fold(
                    onSuccess = { response ->
                        if (response.items.isNotEmpty()) {
                            val lastMsg = response.items.first()
                            if (lastMsg.deleted) {
                                val me = currentUserId.value
                                val isFromMe = lastMsg.senderId == me
                                val displayText = if (isFromMe) "Bạn: Tin nhắn đã bị thu hồi" else "Tin nhắn đã bị thu hồi"
                                
                                _conversations.value = _conversations.value.map { conv ->
                                    if (conv.id == conversationId) {
                                        conv.copy(lastMessage = displayText)
                                    } else {
                                        conv
                                    }
                                }
                            }
                        }
                        // Mark as processed
                        decryptedConversations.add(conversationId)
                    },
                    onFailure = { error ->
                        android.util.Log.e("ChatViewModel", "Failed to check deleted status for $conversationId", error)
                        decryptedConversations.add(conversationId)
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error checking deleted status", e)
                decryptedConversations.add(conversationId)
            }
        }
    }

    private fun updateMessageByClientMessageId(clientMessageId: String, transform: (Message) -> Message) {
        _messages.value = _messages.value.map { msg ->
            if (msg.clientMessageId == clientMessageId) transform(msg) else msg
        }
    }

    private fun scheduleMediaDownloads(messages: List<Message>) {
        messages.forEach { message ->
            if (message.mediaId != null &&
                message.mediaStatus == MediaStatus.DOWNLOADING &&
                !message.conversationId.isNullOrBlank()
            ) {
                downloadMediaForMessage(message.id, message.mediaId, message.conversationId!!)
            }
        }
    }

    private fun downloadMediaForMessage(messageId: String, mediaId: String, conversationId: String) {
        if (downloadingMediaIds.contains(mediaId)) return
        downloadingMediaIds.add(mediaId)
        updateMessageById(messageId) { it.copy(mediaStatus = MediaStatus.DOWNLOADING) }

        viewModelScope.launch {
            repository.downloadMedia(mediaId, conversationId).fold(
                onSuccess = { result ->
                    downloadingMediaIds.remove(mediaId)
                    updateMessageById(messageId) { msg ->
                        msg.copy(
                            mediaLocalPath = result.localPath,
                            mediaStatus = MediaStatus.READY
                        )
                    }
                },
                onFailure = { error ->
                    downloadingMediaIds.remove(mediaId)
                    updateMessageById(messageId) { msg -> msg.copy(mediaStatus = MediaStatus.FAILED) }
                    _messagesError.value = error.message
                }
            )
        }
    }

    private fun updateMessageById(messageId: String, transform: (Message) -> Message) {
        _messages.value = _messages.value.map { msg -> if (msg.id == messageId) transform(msg) else msg }
    }
    
    override fun onCleared() {
        super.onCleared()
        disconnectWebSocket()
    }
}
