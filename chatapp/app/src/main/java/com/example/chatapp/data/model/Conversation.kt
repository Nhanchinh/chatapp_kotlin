package com.example.chatapp.data.model

data class Conversation(
    val id: String,
    val name: String,
    val lastMessage: String,
    val lastTime: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false
)


