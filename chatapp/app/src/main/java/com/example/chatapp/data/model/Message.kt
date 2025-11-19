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
    val mediaStatus: MediaStatus = MediaStatus.NONE
)

enum class MediaStatus {
    NONE,
    UPLOADING,
    DOWNLOADING,
    READY,
    FAILED
}

