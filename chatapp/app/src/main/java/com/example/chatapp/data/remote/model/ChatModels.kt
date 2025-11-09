package com.example.chatapp.data.remote.model

import com.squareup.moshi.Json

data class ConversationDto(
    @Json(name = "_id") val id: String,
    val participants: List<String> = emptyList(),
    @Json(name = "last_message_at") val lastMessageAt: String? = null,
    @Json(name = "last_message_preview") val lastMessagePreview: String? = null,
    @Json(name = "unread_counters") val unreadCounters: Map<String, Int> = emptyMap(),
    @Json(name = "is_online") val isOnline: Boolean? = null
)

data class ConversationsResponse(
    val items: List<ConversationDto> = emptyList(),
    @Json(name = "next_cursor") val nextCursor: String? = null
)

data class MessageDto(
    @Json(name = "_id") val id: String,
    @Json(name = "sender_id") val senderId: String,
    @Json(name = "receiver_id") val receiverId: String,
    val content: String,
    val timestamp: String,
    @Json(name = "conversation_id") val conversationId: String? = null,
    val delivered: Boolean = false,
    val seen: Boolean = false,
    @Json(name = "client_message_id") val clientMessageId: String? = null
)

data class MessagesResponse(
    val items: List<MessageDto> = emptyList(),
    @Json(name = "next_cursor") val nextCursor: String? = null
)

data class UnreadMessagesResponse(
    val messages: List<MessageDto> = emptyList()
)

data class MarkReadRequest(
    @Json(name = "from_user_id") val fromUserId: String? = null,
    @Json(name = "conversation_id") val conversationId: String? = null
)

data class MarkReadResponse(
    val updated: Int
)

// WebSocket message models
data class WebSocketMessage(
    val from: String? = null,
    val to: String? = null,
    val content: String? = null,
    val type: String? = null, // "typing_start", "typing_stop", "delivered", "seen"
    @Json(name = "message_id") val messageId: String? = null,
    @Json(name = "conversation_id") val conversationId: String? = null,
    @Json(name = "client_message_id") val clientMessageId: String? = null,
    val ack: MessageAck? = null
)

data class MessageAck(
    @Json(name = "message_id") val messageId: String,
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "client_message_id") val clientMessageId: String? = null
)

data class WebSocketAckResponse(
    val ack: MessageAck
)

data class WebSocketMessageResponse(
    val type: String,
    val from: String,
    val content: String,
    val ack: MessageAck
)

