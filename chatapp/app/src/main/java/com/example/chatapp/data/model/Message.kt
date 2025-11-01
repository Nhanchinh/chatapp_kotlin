package com.example.chatapp.data.model

data class Message(
    val id: String,
    val text: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val senderName: String? = null
)

