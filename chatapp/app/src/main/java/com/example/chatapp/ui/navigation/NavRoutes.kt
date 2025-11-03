package com.example.chatapp.ui.navigation

sealed class NavRoutes(val route: String) {
    data object Login : NavRoutes("login")
    data object Home : NavRoutes("home")
    data object Chat : NavRoutes("chat/{contactName}")
    data object ContactInfo : NavRoutes("contactinfo/{contactName}")
    data object FriendRequest : NavRoutes("friendrequest")
    data object FriendsList : NavRoutes("friendslist")
    data object UserProfile : NavRoutes("profile/{username}")
    data object Settings : NavRoutes("settings")
}


