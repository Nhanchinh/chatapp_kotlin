




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
import com.example.chatapp.viewmodel.ChatViewModel
import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig
import com.zegocloud.uikit.ZegoUIKit
import com.zegocloud.uikit.prebuilt.call.config.ZegoNotificationConfig
// --- CÁC IMPORT CHUẨN CHO BẢN SDK MỚI ---
import com.zegocloud.uikit.service.defines.ZegoUIKitSignalingPluginInvitationListener
import com.zegocloud.uikit.service.defines.ZegoUIKitUser
import com.zegocloud.uikit.prebuilt.call.invite.internal.IncomingCallButtonListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatappApplication : Application()

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        // TODO: Đảm bảo số này khớp với AppID ở Backend Python
        private const val ZEGO_APP_ID = 2014683924L

        var pendingCallLogTargetId: String? = null
        var pendingGroupConversationId: String? = null
        var pendingCallType: String? = null
        var pendingGroupCallType: String? = null
    }

    private lateinit var authViewModelRef: AuthViewModel

    private val chatViewModel: ChatViewModel by lazy {
        ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[ChatViewModel::class.java]
    }

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
                    LaunchedEffect(authState.isLoggedIn, authState.userId) {
                        if (authState.isLoggedIn && !authState.userId.isNullOrBlank()) {
                            val userId = authState.userId!!
                            val userName = authState.userFullName ?: userId
                            initZegoService(authViewModel, userId, userName)
                        } else {
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

    override fun onResume() {
        super.onResume()
        forceStopAudioHardware()
    }

    override fun onDestroy() {
        super.onDestroy()
        forceStopAudioHardware()
        MyFirebaseMessagingService.onNewToken = null
        ZegoUIKitPrebuiltCallService.unInit()
    }

    private fun forceStopAudioHardware() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.abandonAudioFocus(null)

            val localUser = ZegoUIKit.getLocalUser()
            if (localUser != null && !localUser.userID.isNullOrEmpty()) {
                ZegoUIKit.turnMicrophoneOn(localUser.userID, false)
                ZegoUIKit.turnCameraOn(localUser.userID, false)
            }

            ZegoUIKit.stopPublishingStream()
            ZegoUIKit.stopPreview()
        } catch (e: Exception) { }
    }

    private fun initZegoService(viewModel: AuthViewModel, userId: String, userName: String) {
        lifecycleScope.launch {
            Log.d(TAG, "🚀 Start Init Zego: $userId")

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

                    val config = ZegoUIKitPrebuiltCallInvitationConfig()
                    val notificationConfig = ZegoNotificationConfig()
                    notificationConfig.sound = "zego_uikit_sound_call"
                    notificationConfig.channelID = "CallInvitation"
                    notificationConfig.channelName = "Call Invitation"
                    config.notificationConfig = notificationConfig

                    ZegoUIKitPrebuiltCallService.init(
                        application,
                        appId,
                        "",
                        userId,
                        userName,
                        config
                    )

                    ZegoUIKit.renewToken(token)
                    ZegoUIKit.getSignalingPlugin().renewToken(token)
                    delay(200)

                    try {
                        ZegoUIKit.getLocalUser()?.let { it.userName = userName }
                        ZegoUIKit.getSignalingPlugin().login(userId, userName, null)
                        Log.d(TAG, "✅ Zego Login Success")
                    } catch (e: Exception) { }

                    // --- HÀM RESET DÙNG CHUNG ---
                    fun doHardReset(reason: String) {
                        Log.d(TAG, "📞 $reason. Resetting...")
                        forceStopAudioHardware()
                        ZegoUIKitPrebuiltCallService.unInit()
                        lifecycleScope.launch {
                            delay(1000)
                            initZegoService(viewModel, userId, userName)
                        }
                    }

                    // 1. SỰ KIỆN KẾT THÚC CUỘC GỌI
                    ZegoUIKitPrebuiltCallService.events.callEvents.setCallEndListener { callEndParam, _ ->
                        forceStopAudioHardware()
                        ZegoUIKit.leaveRoom()

                        // Gửi log cuộc gọi 1-1
                        val toId = pendingCallLogTargetId
                        val isVideo = (pendingCallType ?: "video") == "video"
                        val content = if (isVideo) "Cuộc gọi video kết thúc" else "Cuộc gọi thoại kết thúc"
                        val msgType = if (isVideo) "call_log_video" else "call_log_audio"

                        if (!toId.isNullOrBlank()) {
                            chatViewModel.sendCallLogMessage(content = content, messageType = msgType, to = toId)
                            pendingCallLogTargetId = null
                            pendingCallType = null
                        }

                        // Gửi log cuộc gọi nhóm
                        val groupConvId = pendingGroupConversationId
                        if (!groupConvId.isNullOrBlank()) {
                            chatViewModel.sendGroupCallLogMessage(conversationId = groupConvId, content = content, messageType = msgType)
                            pendingGroupConversationId = null
                            pendingGroupCallType = null
                        }
                    }

                    // 2. SỰ KIỆN TỪ CHỐI / TIMEOUT (Dùng Interface chuẩn của SDK mới)
                    ZegoUIKit.getSignalingPlugin().addInvitationListener(object : ZegoUIKitSignalingPluginInvitationListener {

                        // Bị từ chối
                        override fun onInvitationRefused(invitee: ZegoUIKitUser?, data: String?) {
                            val inviteeID = invitee?.userID // Lấy ID từ đối tượng User
                            Log.d(TAG, "❌ Invitation Refused by: $inviteeID")

                            doHardReset("Invitation Refused")

                            val toId = pendingCallLogTargetId ?: inviteeID
                            if (!toId.isNullOrBlank()) {
                                chatViewModel.sendCallLogMessage(
                                    content = "Đối phương đã từ chối cuộc gọi",
                                    messageType = "rejected_call",
                                    to = toId
                                )
                                pendingCallLogTargetId = null
                            }
                        }

                        // Không trả lời (Timeout phía người gọi) -> SDK dùng onInvitationResponseTimeout
                        override fun onInvitationResponseTimeout(invitees: MutableList<ZegoUIKitUser>?, data: String?) {
                            doHardReset("Response Timeout")
                            val toId = pendingCallLogTargetId
                            if (!toId.isNullOrBlank()) {
                                chatViewModel.sendCallLogMessage(
                                    content = "Không trả lời",
                                    messageType = "missed_call",
                                    to = toId
                                )
                                pendingCallLogTargetId = null
                            }
                        }

                        // Các hàm bắt buộc khác (để trống)
                        override fun onInvitationTimeout(inviter: ZegoUIKitUser?, data: String?) {
                            // Sự kiện này thường xảy ra ở phía người nhận khi hết giờ
                            doHardReset("Invitation Timeout")
                        }
                        override fun onInvitationReceived(inviter: ZegoUIKitUser?, type: Int, data: String?) {}
                        override fun onInvitationAccepted(invitee: ZegoUIKitUser?, data: String?) {}
                        override fun onInvitationCanceled(inviter: ZegoUIKitUser?, data: String?) {}
                    })

                    // 3. Auto Renew
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