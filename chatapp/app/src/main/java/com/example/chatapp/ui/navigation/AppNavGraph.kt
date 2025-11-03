package com.example.chatapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.chatapp.ui.chat.ChatScreen
import com.example.chatapp.ui.chat.ContactInfoScreen
import com.example.chatapp.ui.home.FriendRequestScreen
import com.example.chatapp.ui.home.FriendsListScreen
import com.example.chatapp.ui.home.HomeScreen
import com.example.chatapp.ui.login.LoginScreen
import com.example.chatapp.ui.profile.UserProfileScreen
import com.example.chatapp.viewmodel.AuthViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    isLoggedIn: Boolean
) {
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
                authViewModel = authViewModel
            )
        }
        composable(NavRoutes.Home.route) {
            HomeScreen(
                navController = navController,
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(NavRoutes.Home.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(NavRoutes.Chat.route) { backStackEntry ->
            val contactName = backStackEntry.arguments?.getString("contactName") ?: "Unknown"
            ChatScreen(
                contactName = contactName,
                onBack = { navController.popBackStack() },
                onInfoClick = {
                    navController.navigate("contactinfo/$contactName")
                }
            )
        }
        composable(NavRoutes.ContactInfo.route) { backStackEntry ->
            val contactName = backStackEntry.arguments?.getString("contactName") ?: "Unknown"
            ContactInfoScreen(
                contactName = contactName,
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
                onMessageClick = { friendName ->
                    navController.navigate("chat/$friendName")
                }
            )
        }
        composable(NavRoutes.UserProfile.route) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: "chính.thannhan.50"
            UserProfileScreen(
                username = username,
                onBack = { navController.popBackStack() }
            )
        }
    }
}


