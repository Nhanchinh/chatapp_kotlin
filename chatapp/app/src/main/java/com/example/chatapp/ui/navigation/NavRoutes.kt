package com.example.chatapp.ui.navigation

import android.net.Uri

sealed class NavRoutes(val route: String) {
    data object Login : NavRoutes("login")
    data object ForgotPassword : NavRoutes("forgot-password")
    data object Home : NavRoutes("home")
    data object Chat : NavRoutes("chat/{contactId}?contactName={contactName}&conversationId={conversationId}&isGroup={isGroup}") {
        fun createRoute(contactId: String, contactName: String?, conversationId: String?, isGroup: Boolean = false): String {
            val encodedName = Uri.encode(contactName ?: "")
            val encodedConversationId = Uri.encode(conversationId ?: "")
            val safeContactId = Uri.encode(contactId)
            val isGroupFlag = if (isGroup) "1" else "0"
            return "chat/$safeContactId?contactName=$encodedName&conversationId=$encodedConversationId&isGroup=$isGroupFlag"
        }
    }
    data object ContactInfo : NavRoutes("contactinfo/{contactName}/{contactId}?conversationId={conversationId}") {
        fun createRoute(contactName: String, contactId: String?, conversationId: String? = null): String {
            val encodedName = Uri.encode(contactName)
            val encodedId = Uri.encode(contactId ?: "unknown")
            val encodedConversationId = Uri.encode(conversationId ?: "")
            return "contactinfo/$encodedName/$encodedId?conversationId=$encodedConversationId"
        }
    }
    data object GroupInfo : NavRoutes("groupinfo/{conversationId}?groupName={groupName}") {
        fun createRoute(conversationId: String, groupName: String?): String {
            val encodedName = Uri.encode(groupName ?: "")
            val encodedConversationId = Uri.encode(conversationId)
            return "groupinfo/$encodedConversationId?groupName=$encodedName"
        }
    }
    data object MediaGallery : NavRoutes("media-gallery/{conversationId}?contactName={contactName}") {
        fun createRoute(conversationId: String, contactName: String): String {
            val encodedConversationId = Uri.encode(conversationId)
            val encodedName = Uri.encode(contactName)
            return "media-gallery/$encodedConversationId?contactName=$encodedName"
        }
    }
    data object MediaViewer :
        NavRoutes("media-viewer/{conversationId}/{mediaId}?mimeType={mimeType}") {
        fun createRoute(conversationId: String, mediaId: String, mimeType: String?): String {
            val encodedConversationId = Uri.encode(conversationId)
            val encodedMediaId = Uri.encode(mediaId)
            val encodedMime = Uri.encode(mimeType ?: "")
            return "media-viewer/$encodedConversationId/$encodedMediaId?mimeType=$encodedMime"
        }
    }
    data object OtherUserProfile : NavRoutes("otherprofile/{userId}")
    data object FriendRequest : NavRoutes("friendrequest")
    data object FriendsList : NavRoutes("friendslist")
    data object UserProfile : NavRoutes("profile/{username}")
    data object Settings : NavRoutes("settings")
    data object QRCodeScanner : NavRoutes("qr-scanner")
    data object MyQRCode : NavRoutes("my-qr-code")
    data object KeyBackup : NavRoutes("key-backup")
}


