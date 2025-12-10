package com.example.chatapp

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.chatapp.service.MyFirebaseMessagingService
import com.example.chatapp.ui.navigation.AppNavGraph
import com.example.chatapp.ui.theme.ChatappTheme
import com.example.chatapp.util.FCMManager
import com.example.chatapp.viewmodel.AuthViewModel

class ChatappApplication : Application()

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lock screen orientation to portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        
        setContent {
            ChatappTheme {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = viewModel(
                    factory = ViewModelFactory(applicationContext as Application)
                )
                val authState by authViewModel.authState.collectAsState()
                
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (authState.isInitialized) {
                        AppNavGraph(
                            navController = navController,
                            authViewModel = authViewModel,
                            isLoggedIn = authState.isLoggedIn,
                            notificationData = null // No notification navigation
                        )
                    } else {
                        // Show loading indicator while checking auth state
                        CircularProgressIndicator()
                    }
                }
            }
        }
        
        // Setup FCM token listener
        MyFirebaseMessagingService.onNewToken = { token ->
            Log.d(TAG, "New FCM token received: $token")
            // Token will be sent to server when user logs in
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        MyFirebaseMessagingService.onNewToken = null
    }
}

// Simple ViewModel factory
class ViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
