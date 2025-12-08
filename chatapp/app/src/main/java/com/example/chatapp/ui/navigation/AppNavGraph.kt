package com.example.chatapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.chatapp.ui.chat.ChatScreen
import com.example.chatapp.ui.chat.ContactInfoScreen
import com.example.chatapp.ui.chat.MediaGalleryScreen
import com.example.chatapp.ui.chat.MediaViewerScreen
import com.example.chatapp.ui.home.FriendRequestScreen
import com.example.chatapp.ui.home.FriendsListScreen
import com.example.chatapp.ui.home.HomeScreen
import com.example.chatapp.ui.login.LoginScreen
import com.example.chatapp.ui.login.ForgotPasswordScreen
import com.example.chatapp.ui.profile.UserProfileScreen
import com.example.chatapp.ui.qrcode.MyQRCodeScreen
import com.example.chatapp.ui.qrcode.QRCodeScannerScreen
import com.example.chatapp.ui.settings.SettingsScreen
import com.example.chatapp.viewmodel.AuthViewModel
import com.example.chatapp.viewmodel.ChatViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    isLoggedIn: Boolean
) {
    val chatViewModel: ChatViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) NavRoutes.Home.route else NavRoutes.Login.route
    ) {
        composable(NavRoutes.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(NavRoutes.Home.route) {
                        popUpTo(NavRoutes.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                authViewModel = authViewModel,
                onForgotPassword = {
                    navController.navigate(NavRoutes.ForgotPassword.route)
                }
            )
        }
        composable(NavRoutes.ForgotPassword.route) {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack() },
                onFinished = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.Home.route) {
            HomeScreen(
                navController = navController,
                authViewModel = authViewModel,
                chatViewModel = chatViewModel,
                onLogout = {
                    // Disconnect WebSocket trước khi logout để clear presence
                    chatViewModel.disconnectWebSocket()
                    // Clear chat data
                    chatViewModel.clearCurrentChat()
                    // Logout
                    authViewModel.logout()
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(NavRoutes.Home.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = NavRoutes.Chat.route,
            arguments = listOf(
                navArgument("contactId") {},
                navArgument("contactName") { defaultValue = "" },
                navArgument("conversationId") { defaultValue = "" }
            )
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: return@composable
            val contactName = backStackEntry.arguments?.getString("contactName").orEmpty().ifBlank { null }
            val conversationId = backStackEntry.arguments?.getString("conversationId").orEmpty().ifBlank { null }

            ChatScreen(
                chatViewModel = chatViewModel,
                contactId = contactId,
                contactName = contactName,
                conversationId = conversationId,
                onBack = { navController.popBackStack() },
                onInfoClick = {
                    val safeName = contactName ?: contactId
                    navController.navigate(
                        NavRoutes.ContactInfo.createRoute(safeName, contactId, conversationId)
                    )
                },
                onMediaClick = { _, mediaId, convoId, mimeType ->
                    // Check if it's a file (not image/video)
                    val isFile = mimeType != null && 
                        !mimeType.startsWith("image/") && 
                        !mimeType.startsWith("video/")
                    
                    if (!isFile) {
                        // For images/videos: open viewer
                        navController.navigate(
                            NavRoutes.MediaViewer.createRoute(
                                convoId ?: "",
                                mediaId,
                                mimeType
                            )
                        )
                    }
                    // For files: handled in ChatScreen with download button toggle
                }
            )
        }
        composable(
            route = NavRoutes.ContactInfo.route,
            arguments = listOf(
                navArgument("contactName") {},
                navArgument("contactId") {},
                navArgument("conversationId") { defaultValue = "" }
            )
        ) { backStackEntry ->
            val contactName = backStackEntry.arguments?.getString("contactName") ?: "Unknown"
            val contactId = backStackEntry.arguments?.getString("contactId")
                ?.takeIf { it != "unknown" && it.isNotBlank() }
            val conversationId = backStackEntry.arguments?.getString("conversationId")
                ?.takeIf { it.isNotBlank() }
            ContactInfoScreen(
                contactName = contactName,
                contactId = contactId,
                conversationId = conversationId,
                chatViewModel = chatViewModel,
                onBack = { navController.popBackStack() },
                onDeleteConversation = {
                    // Navigate to home after deletion, clearing the back stack
                    navController.navigate(NavRoutes.Home.route) {
                        // Pop back to home, removing chat and contact info screens
                        popUpTo(NavRoutes.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                navController = navController
            )
        }
        composable(
            route = NavRoutes.MediaGallery.route,
            arguments = listOf(
                navArgument("conversationId") {},
                navArgument("contactName") { defaultValue = "" }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            val contactName = backStackEntry.arguments?.getString("contactName").orEmpty()
            MediaGalleryScreen(
                conversationId = conversationId,
                contactName = contactName,
                chatViewModel = chatViewModel,
                onBack = { navController.popBackStack() },
                onMediaClick = { item ->
                    navController.navigate(
                        NavRoutes.MediaViewer.createRoute(
                            conversationId,
                            item.mediaId,
                            item.mimeType
                        )
                    )
                }
            )
        }
        composable(
            route = NavRoutes.MediaViewer.route,
            arguments = listOf(
                navArgument("conversationId") {},
                navArgument("mediaId") {},
                navArgument("mimeType") { defaultValue = "" }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            val mediaId = backStackEntry.arguments?.getString("mediaId") ?: return@composable
            val mimeType = backStackEntry.arguments?.getString("mimeType").orEmpty().ifBlank { null }
            MediaViewerScreen(
                conversationId = conversationId,
                mediaId = mediaId,
                mimeType = mimeType,
                chatViewModel = chatViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.OtherUserProfile.route) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            com.example.chatapp.ui.profile.OtherUserProfileScreen(
                userId = userId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.FriendRequest.route) {
            FriendRequestScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.FriendsList.route) {
            FriendsListScreen(
                onBack = { navController.popBackStack() },
                onMessageClick = { friendId, friendName ->
                    navController.navigate(
                        NavRoutes.Chat.createRoute(
                            contactId = friendId,
                            contactName = friendName,
                            conversationId = null
                        )
                    )
                }
            )
        }
        composable(NavRoutes.UserProfile.route) {
            UserProfileScreen(
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.QRCodeScanner.route) {
            QRCodeScannerScreen(
                navController = navController
            )
        }
        composable(NavRoutes.MyQRCode.route) {
            MyQRCodeScreen(
                navController = navController
            )
        }
    }
}


