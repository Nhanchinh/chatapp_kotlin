package com.example.chatapp.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.chatapp.data.encryption.E2EEManager
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.model.MediaItem
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.WebSocketClient
import com.example.chatapp.data.remote.WebSocketEvent
import com.example.chatapp.data.remote.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class ChatRepository(private val context: Context) {
    private val TAG = "ChatRepository"
    private val api = ApiClient.apiService
    private val authManager = AuthManager(context)
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
        private const val MAX_MEDIA_SIZE_BYTES = 5 * 1024 * 1024 // 5MB
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
        clientMessageId: String? = null
    ): Result<Unit> {
        return try {
            val userId = authManager.userId.first()
                ?: return Result.failure(Exception("User ID not found"))
            val token = authManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))
            
            // Try to encrypt message if we have a conversation with encryption setup
            var finalContent = content
            var iv: String? = null
            var isEncrypted = false
            
            if (conversationId != null && e2eeManager.isEncryptionAvailable(conversationId)) {
                try {
                    val encrypted = e2eeManager.encryptMessage(content, conversationId, token)
                    if (encrypted != null) {
                        finalContent = encrypted.ciphertext
                        iv = encrypted.iv
                        isEncrypted = true
                        Log.d(TAG, "Message encrypted successfully")
                    } else {
                        Log.w(TAG, "Encryption failed, sending plaintext")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error encrypting message, sending plaintext", e)
                }
            } else {
                Log.d(TAG, "No encryption available for conversation, sending plaintext")
            }
            
            webSocketClient.sendMessage(userId, to, finalContent, clientMessageId, iv, isEncrypted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMediaMessage(
        to: String,
        conversationId: String,
        clientMessageId: String,
        mediaUri: Uri
    ): Result<MediaSendResult> {
        return try {
            val userId = authManager.userId.first()
                ?: return Result.failure(Exception("User ID not found"))
            val token = authManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val bytes = readBytesFromUri(mediaUri)
            if (bytes.isEmpty()) {
                return Result.failure(Exception("Không đọc được dữ liệu hình ảnh"))
            }
            if (bytes.size > MAX_MEDIA_SIZE_BYTES) {
                return Result.failure(Exception("Ảnh vượt quá giới hạn 5MB"))
            }

            val mimeType = context.contentResolver.getType(mediaUri) ?: "application/octet-stream"

            val encrypted = e2eeManager.encryptBytes(bytes, conversationId, token)
                ?: return Result.failure(Exception("Không thể mã hóa ảnh"))

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
                content = "[Media]",
                clientMessageId = clientMessageId,
                isEncrypted = false,
                mediaId = mediaId,
                mediaMimeType = mimeType,
                mediaSize = bytes.size.toLong()
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

            val decrypted = e2eeManager.decryptBytes(response.mediaData, response.iv, conversationId, token)
                ?: return Result.failure(Exception("Không thể giải mã media"))

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

    suspend fun deleteConversation(conversationId: String): Result<FriendActionResponse> {
        return try {
            val token = authManager.getValidAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.deleteConversation("Bearer $token", conversationId)
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

