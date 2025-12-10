//package com.example.chatapp
//
//import android.app.Application
//import android.content.Intent
//import android.content.pm.ActivityInfo
//import android.os.Bundle
//import android.util.Log
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.enableEdgeToEdge
//import androidx.compose.material3.CircularProgressIndicator
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Surface
//import androidx.compose.runtime.collectAsState
//import androidx.compose.runtime.getValue
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.ViewModelProvider
//import androidx.lifecycle.viewmodel.compose.viewModel
//import androidx.navigation.compose.rememberNavController
//import com.example.chatapp.service.MyFirebaseMessagingService
//import com.example.chatapp.ui.navigation.AppNavGraph
//import com.example.chatapp.ui.theme.ChatappTheme
//import com.example.chatapp.util.FCMManager
//import com.example.chatapp.viewmodel.AuthViewModel
//import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
//import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig
//import com.zegocloud.uikit.service.ZegoUIKit
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.DisposableEffect
//import androidx.compose.ui.platform.LocalLifecycleOwner
//import androidx.lifecycle.Lifecycle
//import androidx.lifecycle.LifecycleEventObserver
//
//class ChatappApplication : Application()
//
//class MainActivity : ComponentActivity() {
//
//    companion object {
//        private const val TAG = "MainActivity"
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        // Lock screen orientation to portrait
//        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
//        enableEdgeToEdge()
//
//        setContent {
//            ChatappTheme {
//                val navController = rememberNavController()
//                val authViewModel: AuthViewModel = viewModel(
//                    factory = ViewModelFactory(applicationContext as Application)
//                )
//                val authState by authViewModel.authState.collectAsState()
//
//                Surface(color = MaterialTheme.colorScheme.background) {
//                    // Init/Uninit Zego Call Invitation Service based on auth state
//                    LaunchedEffect(authState.isLoggedIn, authState.userId) {
//                        if (authState.isLoggedIn && !authState.userId.isNullOrBlank()) {
//                            // Fetch token then init service with token mode (appSign empty)
//                            val tokenResult = authViewModel.fetchZegoToken(roomId = null, expirySeconds = 3 * 24 * 60 * 60)
//                            if (tokenResult.isSuccess) {
//                                val zegoToken = tokenResult.getOrNull()
//                                if (zegoToken != null) {
//                                    val config = ZegoUIKitPrebuiltCallInvitationConfig()
//                                    ZegoUIKitPrebuiltCallService.initWithToken(
//                                        application,
//                                        zegoToken.appId,
//                                        "", // appSign must be empty when using token
//                                        zegoToken.token,
//                                        authState.userId,
//                                        authState.userId, // use userId as userName for now
//                                        config
//                                    )
//                                    Log.d(TAG, "Zego call service initialized with token for user: ${authState.userId}")
//
//                                    // Optional: renew token proactively when SDK requests it
//                                    ZegoUIKit.setTokenWillExpireListener { _ ->
//                                        // Fire-and-forget renewal; failures simply log
//                                        launch {
//                                            val renewed = authViewModel.fetchZegoToken(roomId = null, expirySeconds = 3 * 24 * 60 * 60)
//                                            renewed.getOrNull()?.token?.let { newToken ->
//                                                ZegoUIKit.renewToken(newToken)
//                                                Log.d(TAG, "Zego token renewed")
//                                            }
//                                        }
//                                    }
//                                } else {
//                                    Log.e(TAG, "Zego token result is null")
//                                }
//                            } else {
//                                Log.e(TAG, "Failed to fetch Zego token: ${tokenResult.exceptionOrNull()?.message}")
//                            }
//                        } else {
//                            // Uninit when logged out
//                            ZegoUIKitPrebuiltCallService.unInit()
//                            Log.d(TAG, "Zego call service uninitialized")
//                        }
//                    }
//
//                    if (authState.isInitialized) {
//                        AppNavGraph(
//                            navController = navController,
//                            authViewModel = authViewModel,
//                            isLoggedIn = authState.isLoggedIn,
//                            notificationData = null // No notification navigation
//                        )
//                    } else {
//                        // Show loading indicator while checking auth state
//                        CircularProgressIndicator()
//                    }
//                }
//            }
//        }
//
//        // Setup FCM token listener
//        MyFirebaseMessagingService.onNewToken = { token ->
//            Log.d(TAG, "New FCM token received: $token")
//            // Token will be sent to server when user logs in
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        MyFirebaseMessagingService.onNewToken = null
//    }
//}
//
//// Simple ViewModel factory
//class ViewModelFactory(
//    private val application: Application
//) : ViewModelProvider.Factory {
//    override fun <T : ViewModel> create(modelClass: Class<T>): T {
//        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
//            @Suppress("UNCHECKED_CAST")
//            return AuthViewModel(application) as T
//        }
//        throw IllegalArgumentException("Unknown ViewModel class")
//    }
//}


//
//package com.example.chatapp
//
//import android.app.Application
//import android.content.pm.ActivityInfo
//import android.os.Bundle
//import android.util.Log
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.enableEdgeToEdge
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.material3.CircularProgressIndicator
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Surface
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.collectAsState
//import androidx.compose.runtime.getValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.ViewModelProvider
//import androidx.lifecycle.viewmodel.compose.viewModel
//import androidx.navigation.compose.rememberNavController
//import com.example.chatapp.service.MyFirebaseMessagingService
//import com.example.chatapp.ui.navigation.AppNavGraph
//import com.example.chatapp.ui.theme.ChatappTheme
//import com.example.chatapp.viewmodel.AuthViewModel
//import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
//import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig
//// --- IMPORT QUAN TRỌNG ĐỂ FIX LỖI RENEW TOKEN ---
//import com.zegocloud.uikit.ZegoUIKit
//// ------------------------------------------------
//import kotlinx.coroutines.launch
//
//class ChatappApplication : Application()
//
//class MainActivity : ComponentActivity() {
//
//    companion object {
//        private const val TAG = "MainActivity"
//        // TODO: Đảm bảo số này khớp với AppID ở Backend Python
//        private const val ZEGO_APP_ID = 2014683924L
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        // Lock screen orientation to portrait
//        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
//        enableEdgeToEdge()
//
//        setContent {
//            ChatappTheme {
//                val navController = rememberNavController()
//                val authViewModel: AuthViewModel = viewModel(
//                    factory = ViewModelFactory(applicationContext as Application)
//                )
//                val authState by authViewModel.authState.collectAsState()
//
//                Surface(color = MaterialTheme.colorScheme.background) {
//
//                    // --- LOGIC ZEGO (TOKEN MODE) ---
//                    LaunchedEffect(authState.isLoggedIn, authState.userId) {
//                        if (authState.isLoggedIn && !authState.userId.isNullOrBlank()) {
//                            val userId = authState.userId!!
//                            // Nếu không có tên thì dùng tạm ID
//                            val userName = authState.userFullName ?: userId
//
//                            Log.d(TAG, "🚀 User logged in ($userId). Fetching Zego Token...")
//
//                            // 1. Gọi API lấy Token từ Backend (Hạn 24h)
//                            val tokenResult = authViewModel.fetchZegoToken(expirySeconds = 24 * 60 * 60)
//
//                            if (tokenResult.isSuccess) {
//                                val zegoData = tokenResult.getOrNull()
//                                if (zegoData != null) {
//                                    val token = zegoData.token
//                                    // Ưu tiên lấy AppID từ server trả về, nếu không có thì dùng cứng
//                                    val appId = if (zegoData.appId > 0) zegoData.appId else ZEGO_APP_ID
//
//                                    Log.d(TAG, "✅ Got Token success! AppID: $appId")
//
//                                    val config = ZegoUIKitPrebuiltCallInvitationConfig()
//
//                                    // 2. Init Service (QUAN TRỌNG: AppSign = "")
//                                    ZegoUIKitPrebuiltCallService.init(
//                                        application,
//                                        appId,
//                                        "", // <--- AppSign RỖNG để chạy Token Mode
//                                        userId,
//                                        userName,
//                                        config
//                                    )
//
//                                    // 3. Nạp Token vào SDK (Dùng ZegoUIKit thay vì Service)
//                                    ZegoUIKit.renewToken(token)
//                                    Log.d(TAG, "✅ Zego Service Initialized in Token Mode")
//
//                                    // 4. Lắng nghe sự kiện hết hạn để tự động gia hạn
//                                    ZegoUIKit.setTokenWillExpireListener { seconds: Int ->
//                                        Log.w(TAG, "⚠️ Zego Token sắp hết hạn trong $seconds giây")
//                                        launch {
//                                            val renewResult = authViewModel.fetchZegoToken()
//                                            renewResult.getOrNull()?.token?.let { newToken ->
//                                                ZegoUIKit.renewToken(newToken)
//                                                Log.d(TAG, "✅ Zego Token đã được gia hạn")
//                                            }
//                                        }
//                                    }
//                                }
//                            } else {
//                                Log.e(TAG, "❌ Lỗi lấy Zego token: ${tokenResult.exceptionOrNull()?.message}")
//                            }
//                        } else {
//                            // Khi Logout -> Hủy Zego Service
//                            try {
//                                ZegoUIKitPrebuiltCallService.unInit()
//                                Log.d(TAG, "👋 Đã hủy Zego Service (Logout)")
//                            } catch (e: Exception) {
//                                Log.e(TAG, "Error unInit Zego: ${e.message}")
//                            }
//                        }
//                    }
//                    // ----------------------------------------
//
//                    if (authState.isInitialized) {
//                        AppNavGraph(
//                            navController = navController,
//                            authViewModel = authViewModel,
//                            isLoggedIn = authState.isLoggedIn,
//                            notificationData = null // No notification navigation logic yet
//                        )
//                    } else {
//                        // Show loading indicator while checking auth state
//                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//                            CircularProgressIndicator()
//                        }
//                    }
//                }
//            }
//        }
//
//        // Setup FCM token listener
//        MyFirebaseMessagingService.onNewToken = { token ->
//            Log.d(TAG, "New FCM token received: $token")
//            // Token will be sent to server when user logs in (handled in ViewModel usually)
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        MyFirebaseMessagingService.onNewToken = null
//        // Đảm bảo dọn dẹp khi thoát app
//        ZegoUIKitPrebuiltCallService.unInit()
//    }
//}
//
//// Simple ViewModel factory
//class ViewModelFactory(
//    private val application: Application
//) : ViewModelProvider.Factory {
//    override fun <T : ViewModel> create(modelClass: Class<T>): T {
//        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
//            @Suppress("UNCHECKED_CAST")
//            return AuthViewModel(application) as T
//        }
//        throw IllegalArgumentException("Unknown ViewModel class")
//    }
//}




package com.example.chatapp

import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.chatapp.service.MyFirebaseMessagingService
import com.example.chatapp.ui.navigation.AppNavGraph
import com.example.chatapp.ui.theme.ChatappTheme
import com.example.chatapp.viewmodel.AuthViewModel
import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig
import com.zegocloud.uikit.ZegoUIKit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatappApplication : Application()

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        // TODO: Đảm bảo số này khớp với AppID ở Backend Python
        private const val ZEGO_APP_ID = 2014683924L
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

                    // --- LOGIC KHỞI TẠO ZEGO (Clean & Fix Errors) ---
                    LaunchedEffect(authState.isLoggedIn, authState.userId) {
                        // Chỉ chạy khi đã có UserID và LoggedIn
                        if (authState.isLoggedIn && !authState.userId.isNullOrBlank()) {
                            val userId = authState.userId!!
                            val userName = authState.userFullName ?: userId

                            Log.d(TAG, "🚀 User: $userId. Trạng thái: Start Init Zego...")

                            // 1. Dọn dẹp session cũ ngay lập tức (Fix lỗi Kill App)
                            try {
                                ZegoUIKitPrebuiltCallService.unInit()
                                delay(100) // Delay nhẹ để hệ thống dọn dẹp
                            } catch (e: Exception) {
                                Log.e(TAG, "Cleanup warning: ${e.message}")
                            }

                            // 2. Lấy Token mới từ Backend
                            val tokenResult = authViewModel.fetchZegoToken(expirySeconds = 24 * 60 * 60)

                            if (tokenResult.isSuccess) {
                                val zegoData = tokenResult.getOrNull()
                                if (zegoData != null) {
                                    val token = zegoData.token
                                    val appId = if (zegoData.appId > 0) zegoData.appId else ZEGO_APP_ID

                                    val config = ZegoUIKitPrebuiltCallInvitationConfig()
                                    // Đã bỏ phần config.provider gây lỗi.
                                    // Việc truyền userName vào hàm init bên dưới là đủ để hiện tên.

                                    // 3. Init Service
                                    ZegoUIKitPrebuiltCallService.init(
                                        application,
                                        appId,
                                        "", // Token Mode (AppSign phải rỗng)
                                        userId,
                                        userName, // Truyền tên thật vào đây
                                        config
                                    )

                                    // 4. Nạp Token cho cả Video Engine và Signaling (ZIM)
                                    ZegoUIKit.renewToken(token)
                                    ZegoUIKit.getSignalingPlugin().renewToken(token)

                                    // --- FIX LỖI "Unresolved reference: name" ---
                                    // Ép cập nhật lại Local User Name (thuộc tính đúng là userName)
                                    ZegoUIKit.getLocalUser()?.let {
                                        it.userName = userName // Sửa 'name' thành 'userName'
                                    }

                                    Log.d(TAG, "✅ Zego Service Init & Renew Success (User: $userName)")

                                    // 5. Auto Renew Token
                                    ZegoUIKit.setTokenWillExpireListener { seconds: Int ->
                                        Log.w(TAG, "⚠️ Token sắp hết hạn trong $seconds giây")
                                        launch {
                                            val renewResult = authViewModel.fetchZegoToken()
                                            renewResult.getOrNull()?.token?.let { newToken ->
                                                ZegoUIKit.renewToken(newToken)
                                                ZegoUIKit.getSignalingPlugin().renewToken(newToken)
                                                Log.d(TAG, "✅ Token Renewed Successfully")
                                            }
                                        }
                                    }
                                }
                            } else {
                                Log.e(TAG, "❌ Lỗi lấy token: ${tokenResult.exceptionOrNull()?.message}")
                            }
                        } else {
                            // Logout logic
                            try {
                                ZegoUIKitPrebuiltCallService.unInit()
                                Log.d(TAG, "👋 Zego Service UnInitialized (Logout)")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error unInit: ${e.message}")
                            }
                        }
                    }
                    // ----------------------------------------

                    if (authState.isInitialized) {
                        AppNavGraph(
                            navController = navController,
                            authViewModel = authViewModel,
                            isLoggedIn = authState.isLoggedIn,
                            notificationData = null
                        )
                    } else {
                        // Show loading indicator
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        // Setup FCM token listener
        MyFirebaseMessagingService.onNewToken = { token ->
            Log.d(TAG, "New FCM token received: $token")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MyFirebaseMessagingService.onNewToken = null
        ZegoUIKitPrebuiltCallService.unInit()
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