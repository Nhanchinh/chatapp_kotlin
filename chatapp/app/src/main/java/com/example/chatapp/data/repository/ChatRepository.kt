package com.example.chatapp.data.repository

import android.content.Context
import android.util.Log
import com.example.chatapp.data.encryption.E2EEManager
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.WebSocketClient
import com.example.chatapp.data.remote.WebSocketEvent
import com.example.chatapp.data.remote.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ChatRepository(private val context: Context) {
    private val TAG = "ChatRepository"
    private val api = ApiClient.apiService
    private val authManager = AuthManager(context)
    private val webSocketClient = WebSocketClient()
    private val e2eeManager = E2EEManager(context)

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
}

