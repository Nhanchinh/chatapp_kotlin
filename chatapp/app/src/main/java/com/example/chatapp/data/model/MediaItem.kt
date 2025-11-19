package com.example.chatapp.data.model

data class MediaItem(
    val messageId: String,
    val mediaId: String,
    val conversationId: String,
    val mimeType: String?,
    val size: Long?,
    val timestamp: Long,
    val localPath: String?,
    val isFromMe: Boolean
)

