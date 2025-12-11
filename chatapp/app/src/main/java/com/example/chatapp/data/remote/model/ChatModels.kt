package com.example.chatapp.data.remote.model

import com.squareup.moshi.Json

data class ConversationDto(
    @Json(name = "_id") val id: String,
    val participants: List<String> = emptyList(),
    @Json(name = "last_message_at") val lastMessageAt: String? = null,
    @Json(name = "last_message_preview") val lastMessagePreview: String? = null,
    @Json(name = "last_message_sender_id") val lastMessageSenderId: String? = null,
    @Json(name = "unread_counters") val unreadCounters: Map<String, Int> = emptyMap(),
    @Json(name = "is_online") val isOnline: Boolean? = null,
    @Json(name = "is_group") val isGroup: Boolean? = null,
    val name: String? = null,
    @Json(name = "group_key_version") val groupKeyVersion: Int? = null,
    @Json(name = "owner_id") val ownerId: String? = null
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
    @Json(name = "client_message_id") val clientMessageId: String? = null,
    val iv: String? = null,  // Initialization vector for AES-GCM
    @Json(name = "is_encrypted") val isEncrypted: Boolean = false,
    @Json(name = "media_id") val mediaId: String? = null,
    @Json(name = "media_mime_type") val mediaMimeType: String? = null,
    @Json(name = "media_size") val mediaSize: Long? = null,
    @Json(name = "media_duration") val mediaDuration: Double? = null,
    @Json(name = "key_version") val keyVersion: Int? = null,
    val deleted: Boolean = false,  // True if message is deleted/recalled
    @Json(name = "reply_to") val replyTo: String? = null,  // ID of message being replied to
    val reactions: Map<String, String>? = null,  // {user_id: emoji} - Simple reactions
    @Json(name = "message_type") val messageType: String? = null
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
    @Json(name = "conversation_id") val conversationId: String? = null,
    val content: String? = null,
    val type: String? = null, // "typing_start", "typing_stop", "delivered", "seen"
    @Json(name = "message_id") val messageId: String? = null,
    @Json(name = "client_message_id") val clientMessageId: String? = null,
    val ack: MessageAck? = null,
    val iv: String? = null,  // Initialization vector for AES-GCM
    @Json(name = "is_encrypted") val isEncrypted: Boolean = false,
    @Json(name = "media_id") val mediaId: String? = null,
    @Json(name = "media_mime_type") val mediaMimeType: String? = null,
    @Json(name = "media_size") val mediaSize: Long? = null,
    @Json(name = "media_duration") val mediaDuration: Double? = null,
    @Json(name = "key_version") val keyVersion: Int? = null,
    @Json(name = "reply_to") val replyTo: String? = null,  // ID of message being replied to
    @Json(name = "message_type") val messageType: String? = null
)

data class MessageAck(
    @Json(name = "message_id") val messageId: String,
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "client_message_id") val clientMessageId: String? = null,
    @Json(name = "media_id") val mediaId: String? = null,
    @Json(name = "media_mime_type") val mediaMimeType: String? = null,
    @Json(name = "media_size") val mediaSize: Long? = null,
    @Json(name = "media_duration") val mediaDuration: Double? = null,
    @Json(name = "key_version") val keyVersion: Int? = null
)

data class WebSocketAckResponse(
    val ack: MessageAck
)

data class WebSocketMessageResponse(
    val type: String,
    val from: String,
    val content: String,
    val ack: MessageAck,
    val iv: String? = null,
    @Json(name = "is_encrypted") val isEncrypted: Boolean = false,
    @Json(name = "media_id") val mediaId: String? = null,
    @Json(name = "media_mime_type") val mediaMimeType: String? = null,
    @Json(name = "media_size") val mediaSize: Long? = null,
    @Json(name = "media_duration") val mediaDuration: Double? = null,
    @Json(name = "conversation_id") val conversationId: String? = null,
    @Json(name = "key_version") val keyVersion: Int? = null,
    @Json(name = "reply_to") val replyTo: String? = null,  // ID of message being replied to
    @Json(name = "message_type") val messageType: String? = null
)

data class MediaUploadRequest(
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "media_data") val mediaData: String,
    val iv: String,
    @Json(name = "mime_type") val mimeType: String,
    val size: Int
)

data class MediaUploadResponse(
    @Json(name = "media_id") val mediaId: String
)

data class MediaDownloadResponse(
    @Json(name = "media_id") val mediaId: String,
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "mime_type") val mimeType: String,
    val size: Int,
    val iv: String,
    @Json(name = "media_data") val mediaData: String
)

// Create Conversation Models
data class EncryptedKeyDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "encrypted_session_key") val encryptedSessionKey: String
)

data class CreateConversationRequest(
    @Json(name = "participant_id") val participantId: String,
    val keys: List<EncryptedKeyDto>
)

data class CreateConversationResponse(
    @Json(name = "conversation_id") val conversationId: String,
    val participants: List<String>
)

data class CreateGroupRequest(
    val name: String,
    @Json(name = "member_ids") val memberIds: List<String>,
    val keys: List<EncryptedKeyDto>
)

data class CreateGroupResponse(
    @Json(name = "conversation_id") val conversationId: String,
    val participants: List<String>,
    val name: String,
    @Json(name = "group_key_version") val groupKeyVersion: Int,
    @Json(name = "owner_id") val ownerId: String
)

data class MemberDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "full_name") val fullName: String? = null,
    val email: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

data class GroupInfoResponse(
    @Json(name = "conversation_id") val conversationId: String,
    val name: String? = null,
    val participants: List<MemberDto> = emptyList(),
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "group_key_version") val groupKeyVersion: Int
)

data class AddMembersRequest(
    @Json(name = "member_ids") val memberIds: List<String>,
    val keys: List<EncryptedKeyDto>
)

// FCM Token models
data class FCMTokenRequest(
    @Json(name = "fcm_token") val fcmToken: String,
    @Json(name = "device_id") val deviceId: String? = null,
    @Json(name = "device_type") val deviceType: String = "android"
)

data class FCMTokenResponse(
    val success: Boolean,
    val message: String
)

