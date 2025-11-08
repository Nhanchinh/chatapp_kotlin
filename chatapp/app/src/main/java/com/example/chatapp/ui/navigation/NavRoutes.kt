package com.example.chatapp.ui.navigation

import android.net.Uri

sealed class NavRoutes(val route: String) {
    data object Login : NavRoutes("login")
    data object Home : NavRoutes("home")
    data object Chat : NavRoutes("chat/{contactId}?contactName={contactName}&conversationId={conversationId}") {
        fun createRoute(contactId: String, contactName: String?, conversationId: String?): String {
            val encodedName = Uri.encode(contactName ?: "")
            val encodedConversationId = Uri.encode(conversationId ?: "")
            val safeContactId = Uri.encode(contactId)
            return "chat/$safeContactId?contactName=$encodedName&conversationId=$encodedConversationId"
        }
    }
    data object ContactInfo : NavRoutes("contactinfo/{contactName}")
    data object FriendRequest : NavRoutes("friendrequest")
    data object FriendsList : NavRoutes("friendslist")
    data object UserProfile : NavRoutes("profile/{username}")
    data object Settings : NavRoutes("settings")
    data object QRCodeScanner : NavRoutes("qr-scanner")
    data object MyQRCode : NavRoutes("my-qr-code")
}


