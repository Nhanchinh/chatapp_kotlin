package com.example.chatapp.data.model

data class Conversation(
    val id: String,
    val name: String,
    val lastMessage: String,
    val lastTime: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val participants: List<String> = emptyList(),
    val lastMessageAt: String? = null,
    val lastMessagePreview: String? = null,
    val lastMessageSenderId: String? = null,
    val isGroup: Boolean = false,
    val groupKeyVersion: Int? = null,
    val ownerId: String? = null,
    val avatar: String? = null  // Avatar URL of the other participant
)


