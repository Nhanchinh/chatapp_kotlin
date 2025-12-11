package com.example.chatapp.data.model

data class Message(
    val id: String,
    val text: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val senderName: String? = null,
    val senderId: String? = null,
    val receiverId: String? = null,
    val conversationId: String? = null,
    val delivered: Boolean = false,
    val seen: Boolean = false,
    val clientMessageId: String? = null,
    val mediaId: String? = null,
    val mediaMimeType: String? = null,
    val mediaSize: Long? = null,
    val mediaLocalPath: String? = null,
    val mediaStatus: MediaStatus = MediaStatus.NONE,
    val mediaDuration: Double? = null,
    val deleted: Boolean = false,  // True if message is deleted/recalled
    val replyTo: String? = null,  // ID of message being replied to
    val reactions: Map<String, String>? = null,  // {user_id: emoji} - Simple reactions
    val messageType: String? = null  // text/image/call_log/missed_call/rejected_call...
)

enum class MediaStatus {
    NONE,
    UPLOADING,
    DOWNLOADING,
    READY,
    FAILED
}

