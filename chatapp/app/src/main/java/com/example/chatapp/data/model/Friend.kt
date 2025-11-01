package com.example.chatapp.data.model

data class Friend(
    val id: String,
    val name: String,
    val profileImage: String? = null,
    val mutualFriends: Int,
    val isOnline: Boolean = false,
    val lastSeen: String? = null, // e.g., "53 phút", "3 giờ"
    val avatarColor: Long = 0xFF90CAF9 // Default color for avatar
)

