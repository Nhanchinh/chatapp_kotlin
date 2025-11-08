package com.example.chatapp.data.repository

import android.content.Context
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.WebSocketClient
import com.example.chatapp.data.remote.WebSocketEvent
import com.example.chatapp.data.remote.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ChatRepository(private val context: Context) {
    private val api = ApiClient.apiService
    private val authManager = AuthManager(context)
    private val webSocketClient = WebSocketClient()

    suspend fun getConversations(limit: Int = 20, cursor: String? = null): Result<ConversationsResponse> {
        return try {
            val token = authManager.getAccessTokenOnce() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.getConversations("Bearer $token", limit, cursor)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMessages(conversationId: String, limit: Int = 50, cursor: String? = null): Result<MessagesResponse> {
        return try {
            val token = authManager.getAccessTokenOnce() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.getMessages("Bearer $token", conversationId, limit, cursor)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUnreadMessages(fromUserId: String? = null): Result<UnreadMessagesResponse> {
        return try {
            val token = authManager.getAccessTokenOnce() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.getUnreadMessages("Bearer $token", fromUserId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markRead(fromUserId: String? = null, conversationId: String? = null): Result<MarkReadResponse> {
        return try {
            val token = authManager.getAccessTokenOnce() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.markRead("Bearer $token", MarkReadRequest(fromUserId, conversationId))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun connectWebSocket(resumeSince: Long? = null): Flow<WebSocketEvent> {
        val userId = authManager.userId.first()
        val token = authManager.getAccessTokenOnce()
        
        if (userId == null || token == null) {
            throw IllegalStateException("Not authenticated")
        }

        return webSocketClient.connect(userId, token, resumeSince)
    }

    suspend fun sendMessage(to: String, content: String, clientMessageId: String? = null): Result<Unit> {
        return try {
            val userId = authManager.userId.first()
                ?: return Result.failure(Exception("User ID not found"))
            
            webSocketClient.sendMessage(userId, to, content, clientMessageId)
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
            val token = authManager.getAccessTokenOnce() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.getFriendsList("Bearer $token")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<FriendActionResponse> {
        return try {
            val token = authManager.getAccessTokenOnce() ?: return Result.failure(Exception("Not authenticated"))
            val response = api.deleteConversation("Bearer $token", conversationId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

