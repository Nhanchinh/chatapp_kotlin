//package com.example.chatapp
//
//import android.app.Application
//import android.content.Context
//import android.content.pm.ActivityInfo
//import android.media.AudioManager
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
//import androidx.lifecycle.lifecycleScope
//import androidx.lifecycle.viewmodel.compose.viewModel
//import androidx.navigation.compose.rememberNavController
//import com.example.chatapp.service.MyFirebaseMessagingService
//import com.example.chatapp.ui.navigation.AppNavGraph
//import com.example.chatapp.ui.theme.ChatappTheme
//import com.example.chatapp.viewmodel.AuthViewModel
//import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
//import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig
//
//import com.zegocloud.uikit.ZegoUIKit
//import com.zegocloud.uikit.prebuilt.call.config.ZegoNotificationConfig
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//
//class ChatappApplication : Application()
//
//class MainActivity : ComponentActivity() {
//
//    companion object {
//        private const val TAG = "MainActivity"
//        private const val ZEGO_APP_ID = 2014683924L
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
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
//                    // --- LOGIC GỌI HÀM KHỞI TẠO ---
//                    LaunchedEffect(authState.isLoggedIn, authState.userId) {
//                        if (authState.isLoggedIn && !authState.userId.isNullOrBlank()) {
//                            val userId = authState.userId!!
//                            val userName = authState.userFullName ?: userId
//
//                            // Gọi hàm khởi tạo tách biệt
//                            initZegoService(authViewModel, userId, userName)
//                        } else {
//                            // Logout
//                            try {
//                                forceStopAudioHardware() // Tắt phần cứng trước
//                                ZegoUIKitPrebuiltCallService.unInit()
//                                ZegoUIKit.getSignalingPlugin().logout()
//                            } catch (e: Exception) {}
//                        }
//                    }
//
//                    if (authState.isInitialized) {
//                        AppNavGraph(
//                            navController = navController,
//                            authViewModel = authViewModel,
//                            isLoggedIn = authState.isLoggedIn,
//                            notificationData = null
//                        )
//                    } else {
//                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//                            CircularProgressIndicator()
//                        }
//                    }
//                }
//            }
//        }
//
//        MyFirebaseMessagingService.onNewToken = { token ->
//            Log.d(TAG, "New FCM token received: $token")
//        }
//    }
//
//    // --- HÀM CAN THIỆP PHẦN CỨNG ÂM THANH (Fix triệt để lỗi Mic) ---
//    private fun forceStopAudioHardware() {
//        try {
//            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
//            // 1. Chuyển chế độ về bình thường (Thoát chế độ cuộc gọi)
//            audioManager.mode = AudioManager.MODE_NORMAL
//
//            // 2. Bỏ quyền ưu tiên âm thanh (Abandon Focus)
//            audioManager.abandonAudioFocus(null)
//
//            // 3. Tắt Mic bằng Zego SDK (Fix lỗi: Lấy UserID hiện tại để truyền vào)
//            val localUser = ZegoUIKit.getLocalUser()
//            if (localUser != null && !localUser.userID.isNullOrEmpty()) {
//                ZegoUIKit.turnMicrophoneOn(localUser.userID, false)
//            }
//
//            ZegoUIKit.stopPublishingStream()
//
//            Log.d(TAG, "🔊 Đã cưỡng chế Reset Audio Hardware")
//        } catch (e: Exception) {
//            Log.e(TAG, "Lỗi tắt Audio Hardware: ${e.message}")
//        }
//    }
//
//    // --- HÀM KHỞI TẠO RIÊNG ---
//    private fun initZegoService(authViewModel: AuthViewModel, userId: String, userName: String) {
//        lifecycleScope.launch {
//            Log.d(TAG, "🚀 Bắt đầu quy trình Init Zego cho $userId...")
//
//            try {
//                // Trước khi init, đảm bảo phần cứng sạch sẽ
//                forceStopAudioHardware()
//                ZegoUIKitPrebuiltCallService.unInit()
//                delay(200)
//            } catch (e: Exception) {
//                Log.e(TAG, "Cleanup warning: ${e.message}")
//            }
//
//            val tokenResult = authViewModel.fetchZegoToken(expirySeconds = 24 * 60 * 60)
//
//            if (tokenResult.isSuccess) {
//                val zegoData = tokenResult.getOrNull()
//                if (zegoData != null) {
//                    val token = zegoData.token
//                    val appId = if (zegoData.appId > 0) zegoData.appId else ZEGO_APP_ID
//
//                    Log.d(TAG, "✅ Token OK. Init Service...")
//
//                    val config = ZegoUIKitPrebuiltCallInvitationConfig()
//                    val notificationConfig = ZegoNotificationConfig()
//                    notificationConfig.sound = "zego_uikit_sound_call"
//                    notificationConfig.channelID = "CallInvitation"
//                    notificationConfig.channelName = "Call Invitation"
//                    config.notificationConfig = notificationConfig
//
//                    ZegoUIKitPrebuiltCallService.init(
//                        application,
//                        appId,
//                        "",
//                        userId,
//                        userName,
//                        config
//                    )
//
//                    ZegoUIKit.renewToken(token)
//                    ZegoUIKit.getSignalingPlugin().renewToken(token)
//
//                    delay(200)
//
//                    try {
//                        ZegoUIKit.getLocalUser()?.let { it.userName = userName }
//                        ZegoUIKit.getSignalingPlugin().login(userId, userName, null)
//                    } catch (e: Exception) {
//                        Log.e(TAG, "Force Login Warning: ${e.message}")
//                    }
//
//                    // --- FIX LỖI MIC (HARD RESET + AUDIO MANAGER) ---
//                    ZegoUIKitPrebuiltCallService.events.callEvents.setCallEndListener { callEndParam, _ ->
//                        Log.d(TAG, "📞 Cuộc gọi kết thúc. Thực hiện Hard Reset toàn diện...")
//
//                        // Bước 1: Can thiệp phần cứng âm thanh ngay lập tức
//                        forceStopAudioHardware()
//
//                        // Bước 2: Hủy Service
//                        ZegoUIKitPrebuiltCallService.unInit()
//
//                        // Bước 3: Khởi động lại sau 1s
//                        lifecycleScope.launch {
//                            delay(1000)
//                            Log.d(TAG, "🔄 Đang khởi động lại Zego Service...")
//                            initZegoService(authViewModel, userId, userName)
//                        }
//                    }
//                    // ---------------------------------------------
//
//                    ZegoUIKit.setTokenWillExpireListener { seconds: Int ->
//                        lifecycleScope.launch {
//                            val renewResult = authViewModel.fetchZegoToken()
//                            renewResult.getOrNull()?.token?.let { newToken ->
//                                ZegoUIKit.renewToken(newToken)
//                                ZegoUIKit.getSignalingPlugin().renewToken(newToken)
//                            }
//                        }
//                    }
//                }
//            } else {
//                Log.e(TAG, "❌ Lỗi lấy token Zego")
//            }
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        forceStopAudioHardware() // Đảm bảo tắt khi thoát app
//        MyFirebaseMessagingService.onNewToken = null
//        ZegoUIKitPrebuiltCallService.unInit()
//    }
//}
//
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
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.chatapp.service.MyFirebaseMessagingService
import com.example.chatapp.ui.navigation.AppNavGraph
import com.example.chatapp.ui.theme.ChatappTheme
import com.example.chatapp.viewmodel.AuthViewModel
import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig

import com.zegocloud.uikit.ZegoUIKit
import com.zegocloud.uikit.prebuilt.call.config.ZegoNotificationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatappApplication : Application()

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        // TODO: Đảm bảo số này khớp với AppID ở Backend Python
        private const val ZEGO_APP_ID = 2014683924L
    }

    // Biến để lưu trạng thái user hiện tại
    private var currentUserId: String? = null
    private var currentUserName: String? = null
    private lateinit var authViewModelRef: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()

        setContent {
            ChatappTheme {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = viewModel(
                    factory = ViewModelFactory(applicationContext as Application)
                )
                authViewModelRef = authViewModel
                val authState by authViewModel.authState.collectAsState()

                Surface(color = MaterialTheme.colorScheme.background) {

                    // --- LOGIC GỌI HÀM KHỞI TẠO ---
                    LaunchedEffect(authState.isLoggedIn, authState.userId) {
                        if (authState.isLoggedIn && !authState.userId.isNullOrBlank()) {
                            val userId = authState.userId!!
                            val userName = authState.userFullName ?: userId

                            // Lưu lại để dùng cho việc init lại
                            currentUserId = userId
                            currentUserName = userName

                            // Gọi hàm khởi tạo
                            initZegoService(authViewModel, userId, userName)
                        } else {
                            // Logout
                            try {
                                forceStopAudioHardware()
                                ZegoUIKitPrebuiltCallService.unInit()
                                ZegoUIKit.getSignalingPlugin().logout()
                            } catch (e: Exception) {}
                        }
                    }

                    if (authState.isInitialized) {
                        AppNavGraph(
                            navController = navController,
                            authViewModel = authViewModel,
                            isLoggedIn = authState.isLoggedIn,
                            notificationData = null
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        MyFirebaseMessagingService.onNewToken = { token ->
            Log.d(TAG, "New FCM token received: $token")
        }
    }

    // --- FIX QUAN TRỌNG: Tắt Mic khi quay lại màn hình chính (Cancel/Refuse) ---
    override fun onResume() {
        super.onResume()
        // Mỗi khi MainActivity hiện lên (tức là không còn ở màn hình gọi),
        // ta kiểm tra và tắt phần cứng Audio để tránh kẹt Mic.
        Log.d(TAG, "onResume: Checking Audio Hardware...")
        forceStopAudioHardware()
    }

    override fun onDestroy() {
        super.onDestroy()
        forceStopAudioHardware()
        MyFirebaseMessagingService.onNewToken = null
        ZegoUIKitPrebuiltCallService.unInit()
    }

    // --- HÀM TẮT PHẦN CỨNG (Đã sửa lỗi tham số turnMicrophoneOn) ---
    private fun forceStopAudioHardware() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.abandonAudioFocus(null)

            // Lấy Local User ID để tắt Mic đúng cách
            val localUser = ZegoUIKit.getLocalUser()
            // Chỉ tắt nếu đang bật
            if (localUser != null && !localUser.userID.isNullOrEmpty()) {
                // SỬA LỖI: Truyền userID vào hàm
                ZegoUIKit.turnMicrophoneOn(localUser.userID, false)
                ZegoUIKit.turnCameraOn(localUser.userID, false)
            }

            ZegoUIKit.stopPublishingStream()
            ZegoUIKit.stopPreview()

            Log.d(TAG, "🔊 Audio Hardware Cleaned")
        } catch (e: Exception) {
            // Ignored
        }
    }

    // --- HÀM KHỞI TẠO DỊCH VỤ ---
    private fun initZegoService(viewModel: AuthViewModel, userId: String, userName: String) {
        lifecycleScope.launch {
            Log.d(TAG, "🚀 Start Init Zego: $userId")

            // Dọn dẹp trước
            try {
                ZegoUIKitPrebuiltCallService.unInit()
                delay(200)
            } catch (e: Exception) { }

            val tokenResult = viewModel.fetchZegoToken(expirySeconds = 24 * 60 * 60)

            if (tokenResult.isSuccess) {
                val zegoData = tokenResult.getOrNull()
                if (zegoData != null) {
                    val token = zegoData.token
                    val appId = if (zegoData.appId > 0) zegoData.appId else ZEGO_APP_ID

                    // SỬA LỖI CONFIG: Dùng ZegoNotificationConfig
                    val config = ZegoUIKitPrebuiltCallInvitationConfig()
                    val notificationConfig = ZegoNotificationConfig()
                    notificationConfig.sound = "zego_uikit_sound_call"
                    notificationConfig.channelID = "CallInvitation"
                    notificationConfig.channelName = "Call Invitation"
                    config.notificationConfig = notificationConfig

                    // Init
                    ZegoUIKitPrebuiltCallService.init(
                        application,
                        appId,
                        "",
                        userId,
                        userName,
                        config
                    )

                    // Token & Login
                    ZegoUIKit.renewToken(token)
                    ZegoUIKit.getSignalingPlugin().renewToken(token)
                    delay(200)

                    try {
                        ZegoUIKit.getLocalUser()?.let { it.userName = userName }
                        ZegoUIKit.getSignalingPlugin().login(userId, userName, null)
                        Log.d(TAG, "✅ Zego Login Success")
                    } catch (e: Exception) {
                        Log.e(TAG, "Login Warning: ${e.message}")
                    }

                    // Sự kiện kết thúc cuộc gọi (Nghe xong tắt)
                    ZegoUIKitPrebuiltCallService.events.callEvents.setCallEndListener { _, _ ->
                        Log.d(TAG, "📞 Call Ended. Cleaning up...")
                        forceStopAudioHardware()
                        ZegoUIKit.leaveRoom()
                    }

                    // Auto Renew
                    ZegoUIKit.setTokenWillExpireListener { seconds: Int ->
                        lifecycleScope.launch {
                            val renewResult = viewModel.fetchZegoToken()
                            renewResult.getOrNull()?.token?.let { newToken ->
                                ZegoUIKit.renewToken(newToken)
                                ZegoUIKit.getSignalingPlugin().renewToken(newToken)
                            }
                        }
                    }
                }
            } else {
                Log.e(TAG, "❌ Failed to get Zego Token")
            }
        }
    }
}

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