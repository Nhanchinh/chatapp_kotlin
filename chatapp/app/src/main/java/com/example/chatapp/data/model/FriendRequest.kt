package com.example.chatapp.data.model

data class FriendRequest(
    val id: String,
    val name: String,
    val profileImage: String? = null,
    val mutualFriends: Int? = null,
    val timeAgo: String,
    val avatarColor: Long = 0xFF90CAF9 // Default color for avatar
)

