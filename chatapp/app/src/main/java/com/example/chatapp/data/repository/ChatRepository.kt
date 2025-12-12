package com.example.chatapp.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.chatapp.data.encryption.AESEncryptedData
import com.example.chatapp.data.encryption.CryptoManager
import com.example.chatapp.data.encryption.E2EEManager
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.local.SettingsManager
import com.example.chatapp.data.model.MediaItem
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.ReactRequest
import com.example.chatapp.data.remote.ReactResponse
import com.example.chatapp.data.remote.WebSocketClient
import com.example.chatapp.data.remote.WebSocketEvent
import com.example.chatapp.data.remote.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class ChatRepository(private val context: Context) {
    private val TAG = "ChatRepository"
    private val api = ApiClient.apiService
    private val authManager = AuthManager(context)
    private val settingsManager = SettingsManager(context)
    private val webSocketClient = WebSocketClient()
    private val e2eeManager = E2EEManager(context)
    private val mediaCache = mutableMapOf<String, String>()
    private val mediaCacheDir: File by lazy {
        val dir = File(context.cacheDir, "chat_media_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    companion object {
        private const val MAX_MEDIA_SIZE_BYTES = 5 * 1024 * 1024 // 5MB for images and files
        private const val MAX_VIDEO_SIZE_BYTES = 100 * 1024 * 1024 // 100MB for videos
    }

    suspend fun getConversations(limit: Int = 20, cursor: String? = null): Result<ConversationsResponse> {
        return try {
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.getConversations("Bearer $token", limit, cursor)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMessages(conversationId: String, limit: Int = 50, cursor: String? = null): Result<MessagesResponse> {
        return try {
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.getMessages("Bearer $token", conversationId, limit, cursor)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUnreadMessages(fromUserId: String? = null): Result<UnreadMessagesResponse> {
        return try {
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.getUnreadMessages("Bearer $token", fromUserId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markRead(fromUserId: String? = null, conversationId: String? = null): Result<MarkReadResponse> {
        return try {
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.markRead("Bearer $token", MarkReadRequest(fromUserId, conversationId))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun connectWebSocket(resumeSince: Long? = null): Flow<WebSocketEvent> {
        val userId = authManager.userId.first()
        val token = authManager.getValidAccessToken()
        
        if (userId == null || token == null) {
            throw IllegalStateException("Not authenticated")
        }

        return webSocketClient.connect(userId, token, resumeSince)
    }

    suspend fun sendMessage(
        to: String, 
        content: String, 
        conversationId: String? = null,
        clientMessageId: String? = null,
        replyTo: String? = null,
        messageType: String? = null
    ): Result<Unit> {
        return try {
            val userId = authManager.userId.first()
                ?: return Result.failure(Exception("User ID not found"))
            val token = authManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))
            
            // Try to encrypt message if E2EE is enabled and we have a conversation with encryption setup
            var finalContent = content
            var iv: String? = null
            var isEncrypted = false
            
            // Check if E2EE is enabled in settings
            val e2eeEnabled = settingsManager.getE2EEEnabled()
            
            if (e2eeEnabled && conversationId != null && e2eeManager.isEncryptionAvailable(conversationId)) {
                try {
                    val encrypted = e2eeManager.encryptMessage(content, conversationId, token)
                    if (encrypted != null) {
                        finalContent = encrypted.ciphertext
                        iv = encrypted.iv
                        isEncrypted = true
                        Log.d(TAG, "Message encrypted successfully (E2EE enabled)")
                    } else {
                        Log.w(TAG, "Encryption failed, sending plaintext")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error encrypting message, sending plaintext", e)
                }
            } else {
                if (!e2eeEnabled) {
                    Log.d(TAG, "E2EE is disabled in settings, sending plaintext")
                } else {
                    Log.d(TAG, "No encryption available for conversation, sending plaintext")
                }
            }
            
            webSocketClient.sendMessage(
                from = userId,
                to = to,
                content = finalContent,
                clientMessageId = clientMessageId,
                iv = iv,
                isEncrypted = isEncrypted,
                replyTo = replyTo,
                conversationId = conversationId,
                messageType = messageType
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendGroupMessage(
        conversationId: String,
        content: String,
        clientMessageId: String? = null,
        iv: String? = null,
        isEncrypted: Boolean = false,
        mediaId: String? = null,
        mediaMimeType: String? = null,
        mediaSize: Long? = null,
        mediaDuration: Double? = null,
        replyTo: String? = null,
        keyVersion: Int? = null,
        messageType: String? = null
    ): Result<Unit> {
        return try {
            val userId = authManager.userId.first()
                ?: return Result.failure(Exception("User ID not found"))
            val token = authManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))

            // Try to encrypt message if E2EE is enabled and we have group encryption setup
            var finalContent = content
            var finalIv: String? = iv
            var finalIsEncrypted = isEncrypted
            
            // Check if E2EE is enabled in settings
            val e2eeEnabled = settingsManager.getE2EEEnabled()
            
            if (e2eeEnabled && !isEncrypted && e2eeManager.isEncryptionAvailable(conversationId)) {
                try {
                    val encrypted = e2eeManager.encryptMessage(content, conversationId, token)
                    if (encrypted != null) {
                        finalContent = encrypted.ciphertext
                        finalIv = encrypted.iv
                        finalIsEncrypted = true
                        Log.d(TAG, "Group message encrypted successfully (E2EE enabled)")
                    } else {
                        Log.w(TAG, "Group encryption failed, sending plaintext")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error encrypting group message, sending plaintext", e)
                }
            } else {
                if (!e2eeEnabled) {
                    Log.d(TAG, "E2EE is disabled in settings, sending plaintext group message")
                } else {
                    Log.d(TAG, "No encryption available for group, sending plaintext")
                }
            }

            webSocketClient.sendMessage(
                from = userId,
                to = null,
                content = finalContent,
                clientMessageId = clientMessageId,
                iv = finalIv,
                isEncrypted = finalIsEncrypted,
                mediaId = mediaId,
                mediaMimeType = mediaMimeType,
                mediaSize = mediaSize,
                mediaDuration = mediaDuration,
                replyTo = replyTo,
                conversationId = conversationId,
                keyVersion = keyVersion,
                messageType = messageType
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createGroupWithE2EE(name: String, memberIds: List<String>): Result<CreateGroupResponse> {
        return try {
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val me = authManager.userId.first() ?: return Result.failure(Exception("User ID not found"))
            val allMembers = (memberIds + me).distinct()
            val prepared = e2eeManager.prepareEncryptedKeys(allMembers, token)
                ?: return Result.failure(Exception("Không thể chuẩn bị khóa nhóm"))
            val (encryptedKeys, sessionKey) = prepared
            val request = CreateGroupRequest(
                name = name,
                memberIds = memberIds,
                keys = encryptedKeys.map { EncryptedKeyDto(userId = it.userId, encryptedSessionKey = it.encryptedSessionKey) }
            )
            val response = api.createGroup("Bearer $token", request)
            // Lưu session key local
            e2eeManager.storeSessionKeyForConversation(response.conversationId, sessionKey)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMediaMessage(
        to: String?,
        conversationId: String,
        clientMessageId: String,
        mediaUri: Uri,
        mediaDurationSec: Double? = null,
        contentPlaceholder: String = "[Media]",
        mimeTypeOverride: String? = null,
        messageType: String? = null
    ): Result<MediaSendResult> {
        return try {
            val userId = authManager.userId.first()
                ?: return Result.failure(Exception("User ID not found"))
            val token = authManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val bytes = readBytesFromUri(mediaUri)
            if (bytes.isEmpty()) {
                return Result.failure(Exception("Không đọc được dữ liệu file"))
            }

            val mimeType = mimeTypeOverride ?: (context.contentResolver.getType(mediaUri) ?: "application/octet-stream")
            
            // Check size limit based on media type
            val maxSize = if (mimeType.startsWith("video/")) {
                MAX_VIDEO_SIZE_BYTES
            } else {
                MAX_MEDIA_SIZE_BYTES
            }
            
            if (bytes.size > maxSize) {
                val maxSizeMB = maxSize / (1024.0 * 1024.0)
                val fileSizeMB = bytes.size / (1024.0 * 1024.0)
                return Result.failure(Exception("File vượt quá giới hạn ${maxSizeMB.toInt()}MB (kích thước: ${String.format("%.1f", fileSizeMB)}MB)"))
            }

            // Check if E2EE is enabled in settings
            val e2eeEnabled = settingsManager.getE2EEEnabled()
            
            val encrypted = if (e2eeEnabled) {
                e2eeManager.encryptBytes(bytes, conversationId, token)
                    ?: return Result.failure(Exception("Không thể mã hóa ảnh"))
            } else {
                // E2EE disabled, send plaintext (base64 encoded)
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                com.example.chatapp.data.encryption.AESEncryptedData(
                    ciphertext = base64,
                    iv = ""  // No IV for plaintext
                )
            }

            val uploadResponse = api.uploadMedia(
                "Bearer $token",
                MediaUploadRequest(
                    conversationId = conversationId,
                    mediaData = encrypted.ciphertext,
                    iv = encrypted.iv,
                    mimeType = mimeType,
                    size = bytes.size
                )
            )

            val mediaId = uploadResponse.mediaId
            val localPath = saveBytesToCache(mediaId, bytes, mimeType)

            val sendResult = webSocketClient.sendMessage(
                from = userId,
                to = to,
                content = contentPlaceholder,
                clientMessageId = clientMessageId,
                isEncrypted = false,
                mediaId = mediaId,
                mediaMimeType = mimeType,
                mediaSize = bytes.size.toLong(),
                mediaDuration = mediaDurationSec,
                conversationId = conversationId,
                messageType = messageType
            )

            if (sendResult.isFailure) {
                return Result.failure(sendResult.exceptionOrNull() ?: Exception("Không thể gửi thông điệp media"))
            }

            Result.success(
                MediaSendResult(
                    mediaId = mediaId,
                    localPath = localPath,
                    mimeType = mimeType,
                    size = bytes.size.toLong()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendVoiceMessage(
        to: String?,
        conversationId: String,
        clientMessageId: String,
        mediaUri: Uri,
        mediaDurationSec: Double
    ): Result<MediaSendResult> {
        return sendMediaMessage(
            to = to,
            conversationId = conversationId,
            clientMessageId = clientMessageId,
            mediaUri = mediaUri,
            mediaDurationSec = mediaDurationSec,
            contentPlaceholder = "[Voice]",
            mimeTypeOverride = "audio/mp4",
            messageType = null
        )
    }

    suspend fun downloadMedia(
        mediaId: String,
        conversationId: String
    ): Result<MediaDownloadResult> {
        return try {
            mediaCache[mediaId]?.let { cachedPath ->
                if (File(cachedPath).exists()) {
                    return Result.success(MediaDownloadResult(mediaId, cachedPath, null))
                } else {
                    mediaCache.remove(mediaId)
                }
            }

            val token = authManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val response = api.downloadMedia("Bearer $token", mediaId)

            // Check if media is encrypted: if iv is null or empty, media is plaintext (base64 encoded)
            val decrypted = if (response.iv.isNullOrEmpty()) {
                // Media is not encrypted, just decode base64
                try {
                    android.util.Base64.decode(response.mediaData, android.util.Base64.NO_WRAP)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode plaintext media", e)
                    return Result.failure(Exception("Không thể giải mã media"))
                }
            } else {
                // Media is encrypted, decrypt it
                e2eeManager.decryptBytes(response.mediaData, response.iv, conversationId, token)
                    ?: return Result.failure(Exception("Không thể giải mã media"))
            }

            val localPath = saveBytesToCache(response.mediaId, decrypted, response.mimeType)
            Result.success(MediaDownloadResult(response.mediaId, localPath, response.mimeType))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMediaGallery(
        conversationId: String,
        limit: Int = 200
    ): Result<List<MediaItem>> {
        return try {
            val me = authManager.userId.first()
            val token = authManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val response = api.getMessages("Bearer $token", conversationId, limit, null)
            val items = response.items
                .filter { it.mediaId != null }
                .map { dto ->
                    val mediaId = dto.mediaId!!
                    val localPath = ensureMediaCached(mediaId, conversationId).getOrNull()
                    MediaItem(
                        messageId = dto.id,
                        mediaId = mediaId,
                        conversationId = conversationId,
                        mimeType = dto.mediaMimeType,
                        size = dto.mediaSize,
                        timestamp = parseTimestamp(dto.timestamp),
                        localPath = localPath,
                        isFromMe = dto.senderId == me
                    )
                }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ensureMediaCached(mediaId: String, conversationId: String): Result<String> {
        mediaCache[mediaId]?.let { cached ->
            if (File(cached).exists()) {
                return Result.success(cached)
            } else {
                mediaCache.remove(mediaId)
            }
        }

        return downloadMedia(mediaId, conversationId).map { it.localPath }
    }

    private fun parseTimestamp(value: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(value)?.time
                ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    fun getCachedMediaPath(mediaId: String): String? {
        mediaCache[mediaId]?.let { path ->
            if (File(path).exists()) return path
            mediaCache.remove(mediaId)
        }

        val existing = mediaCacheDir.listFiles()?.firstOrNull { it.name.startsWith(mediaId) }
        return existing?.absolutePath?.also { mediaCache[mediaId] = it }
    }

    suspend fun sendTyping(to: String, isTyping: Boolean): Result<Unit> {
        return try {
            val userId = authManager.userId.first()
                ?: return Result.failure(Exception("User ID not found"))
            
            webSocketClient.sendTyping(userId, to, isTyping)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendDelivered(messageId: String, conversationId: String, to: String): Result<Unit> {
        return try {
            val userId = authManager.userId.first()
                ?: return Result.failure(Exception("User ID not found"))
            
            webSocketClient.sendDelivered(messageId, conversationId, userId, to)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendSeen(messageId: String, conversationId: String, to: String): Result<Unit> {
        return try {
            val userId = authManager.userId.first()
                ?: return Result.failure(Exception("User ID not found"))
            
            webSocketClient.sendSeen(messageId, conversationId, userId, to)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun disconnectWebSocket() {
        webSocketClient.disconnect()
    }

    fun isWebSocketConnected(): Boolean {
        return webSocketClient.isConnected()
    }

    suspend fun getFriendsList(): Result<FriendsListResponse> {
        return try {
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.getFriendsList("Bearer $token")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(messageId: String): Result<FriendActionResponse> {
        return try {
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.deleteMessage("Bearer $token", messageId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reactToMessage(messageId: String, emoji: String): Result<ReactResponse> {
        return try {
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.reactToMessage("Bearer $token", messageId, ReactRequest(emoji))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<FriendActionResponse> {
        return try {
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.deleteConversation("Bearer $token", conversationId)
            
            // Clear local session key for this conversation
            try {
                e2eeManager.clearSessionKeyForConversation(conversationId)
                Log.d(TAG, "Cleared local session key for deleted conversation $conversationId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear local session key for conversation $conversationId", e)
            }
            
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setOffline(): Result<Unit> {
        return try {
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            api.setOffline("Bearer $token")
            Result.success(Unit)
        } catch (e: Exception) {
            // Ignore errors khi set offline (có thể token đã expired)
            Result.success(Unit)
        }
    }
    
    /**
     * Setup E2EE encryption for a conversation
     * Call this when opening a conversation for the first time
     */
    suspend fun setupConversationEncryption(
        conversationId: String,
        participantIds: List<String>
    ): Result<Boolean> {
        return try {
            val token = authManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))
            
            val success = e2eeManager.setupConversationEncryption(
                conversationId,
                participantIds,
                token
            )
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up conversation encryption", e)
            Result.failure(e)
        }
    }
    
    /**
     * Decrypt a received message
     */
    suspend fun decryptMessage(
        ciphertext: String,
        iv: String,
        conversationId: String
    ): String? {
        return try {
            val token = authManager.getValidAccessToken() ?: return null
            e2eeManager.decryptMessage(ciphertext, iv, conversationId, token)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting message", e)
            null
        }
    }
    
    /**
     * Check if encryption is available for a conversation
     * Thread-safe operation
     */
    suspend fun isEncryptionAvailable(conversationId: String): Boolean {
        return e2eeManager.isEncryptionAvailable(conversationId)
    }
    
    /**
     * Delete outdated conversation key (both from server and local storage).
     * This is used when the encrypted key cannot be decrypted due to key mismatch.
     */
    suspend fun deleteOutdatedConversationKey(conversationId: String): Result<Boolean> {
        return try {
            val token = authManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))
            
            val deleted = e2eeManager.deleteOutdatedConversationKey(conversationId, token)
            Result.success(deleted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getZegoToken(roomId: String?, expirySeconds: Int = 3600): Result<com.example.chatapp.data.remote.model.ZegoTokenResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = authManager.getValidAccessToken()
                    ?: return@withContext Result.failure(Exception("Not authenticated"))
                val body = com.example.chatapp.data.remote.model.ZegoTokenRequest(
                    roomId = roomId,
                    expirySeconds = expirySeconds
                )
                val resp = api.getZegoToken("Bearer $token", body)
                Result.success(resp)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Zego token", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Create a new conversation with encryption keys.
     * This follows the new flow:
     * 1. Prepare encrypted keys for all participants
     * 2. Call createConversation API (server creates conversation + stores keys)
     * 3. Store session key locally
     * 4. Return conversation ID
     * 
     * @param participantId The other participant's user ID
     * @return Result with conversation ID or error
     */
    suspend fun createConversation(participantId: String): Result<String> {
        return try {
            val me = authManager.userId.first()
                ?: return Result.failure(Exception("User ID not found"))
            val token = authManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))
            
            val participantIds = listOf(me, participantId)
            
            // Prepare encrypted keys
            val prepared = e2eeManager.prepareEncryptedKeys(participantIds, token)
                ?: return Result.failure(Exception("Failed to prepare encrypted keys"))
            
            val (encryptedKeys, sessionKey) = prepared
            
            // Convert to DTO format for API
            val keyDtos = encryptedKeys.map { key ->
                com.example.chatapp.data.remote.model.EncryptedKeyDto(
                    userId = key.userId,
                    encryptedSessionKey = key.encryptedSessionKey
                )
            }
            
            // Create conversation with keys
            val request = com.example.chatapp.data.remote.model.CreateConversationRequest(
                participantId = participantId,
                keys = keyDtos
            )
            
            val response = api.createConversation("Bearer $token", request)
            
            // Store session key locally using E2EEManager's internal method
            // We need to store it through the manager to ensure proper handling
            // Since keyManager is private, we'll use a workaround: store via setup method
            // Actually, we can create a helper method or just store it directly
            // For now, let's add a method to E2EEManager to store session key
            e2eeManager.storeSessionKeyForConversation(response.conversationId, sessionKey)
            
            Log.d(TAG, "Successfully created conversation ${response.conversationId} with encryption")
            Result.success(response.conversationId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating conversation", e)
            Result.failure(e)
        }
    }

    suspend fun createGroupEncrypted(name: String, memberIds: List<String>): Result<String> {
        return try {
            val me = authManager.userId.first()
                ?: return Result.failure(Exception("User ID not found"))
            val token = authManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))
            val participants = (memberIds + me).distinct()
            val prepared = e2eeManager.prepareEncryptedKeys(participants, token)
                ?: return Result.failure(Exception("Failed to prepare encrypted keys"))
            val (encryptedKeys, sessionKey) = prepared
            val keyDtos = encryptedKeys.map { key ->
                EncryptedKeyDto(
                    userId = key.userId,
                    encryptedSessionKey = key.encryptedSessionKey
                )
            }
            val response = api.createGroup(
                "Bearer $token",
                CreateGroupRequest(
                    name = name,
                    memberIds = participants,
                    keys = keyDtos
                )
            )
            e2eeManager.storeSessionKeyForConversation(response.conversationId, sessionKey)
            Result.success(response.conversationId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating group", e)
            Result.failure(e)
        }
    }

    suspend fun addGroupMembers(conversationId: String, memberIds: List<String>): Result<GroupInfoResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = authManager.getValidAccessToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
                
                // Get EXISTING group key (don't create new key!)
                val existingKey = e2eeManager.getSessionKeyForConversation(conversationId)
                    ?: return@withContext Result.failure(Exception("Không tìm thấy khóa nhóm hiện tại"))
                
                // Fetch public keys for new members
                val userIdsParam = memberIds.joinToString(",")
                Log.d(TAG, "Fetching public keys for new members: $userIdsParam")
                val publicKeysResponse = api.getPublicKeys("Bearer $token", userIdsParam)
                
                if (publicKeysResponse.items.isEmpty()) {
                    return@withContext Result.failure(Exception("Không tìm thấy public key của thành viên"))
                }
                
                Log.d(TAG, "Fetched ${publicKeysResponse.items.size} public keys from server")
                
                // Encrypt existing key for new members only
                val keyDtos = mutableListOf<EncryptedKeyDto>()
                for (keyDto in publicKeysResponse.items) {
                    val publicKeyPem = keyDto.publicKey
                    if (publicKeyPem == null) {
                        Log.w(TAG, "No public key for user ${keyDto.userId}")
                        continue
                    }
                    
                    try {
                        // Decode public key and encrypt existing group key
                        val publicKey = CryptoManager.decodePublicKey(publicKeyPem)
                        val sessionKeyBytes = existingKey.encoded
                        val encryptedKey = CryptoManager.rsaEncrypt(sessionKeyBytes, publicKey)
                        
                        keyDtos.add(EncryptedKeyDto(userId = keyDto.userId, encryptedSessionKey = encryptedKey))
                        Log.d(TAG, "✅ Encrypted existing group key for member ${keyDto.userId}")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error encrypting key for member ${keyDto.userId}", e)
                        return@withContext Result.failure(Exception("Không thể mã hóa khóa cho thành viên: ${e.message}"))
                    }
                }
                
                if (keyDtos.isEmpty()) {
                    return@withContext Result.failure(Exception("Không thể mã hóa khóa cho bất kỳ thành viên nào"))
                }
                
                val request = AddMembersRequest(memberIds = memberIds, keys = keyDtos)
                val resp = api.addGroupMembers("Bearer $token", conversationId, request)
                Log.d(TAG, "Successfully added ${memberIds.size} members to group")
                // Don't change owner's key - it stays the same!
                Result.success(resp)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding group members", e)
                Result.failure(e)
            }
        }
    }

    suspend fun removeGroupMember(conversationId: String, memberId: String): Result<GroupInfoResponse> {
        return try {
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val resp = api.removeGroupMember("Bearer $token", conversationId, memberId)
            Result.success(resp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(conversationId: String): Result<GroupInfoResponse> {
        return try {
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val resp = api.leaveGroup("Bearer $token", conversationId)
            Result.success(resp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroupInfo(conversationId: String): Result<GroupInfoResponse> {
        return try {
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val resp = api.getGroupInfo("Bearer $token", conversationId)
            Result.success(resp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private fun readBytesFromUri(uri: Uri): ByteArray {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val data = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(data)
                    if (read == -1) break
                    buffer.write(data, 0, read)
                }
                buffer.toByteArray()
            } ?: ByteArray(0)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading bytes from uri $uri", e)
            ByteArray(0)
        }
    }

    private fun saveBytesToCache(mediaId: String, data: ByteArray, mimeType: String): String {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
        val file = File(mediaCacheDir, "$mediaId.$extension")
        return try {
            FileOutputStream(file).use { it.write(data) }
            mediaCache[mediaId] = file.absolutePath
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving media to cache", e)
            file.absolutePath
        }
    }
}

data class MediaSendResult(
    val mediaId: String,
    val localPath: String,
    val mimeType: String,
    val size: Long
)

data class MediaDownloadResult(
    val mediaId: String,
    val localPath: String,
    val mimeType: String?
)

