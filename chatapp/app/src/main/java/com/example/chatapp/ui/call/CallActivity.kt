package com.example.chatapp.ui.call

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.chatapp.BuildConfig
import com.example.chatapp.R
import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallConfig
import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallFragment

class CallActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APP_ID = "appID"
        const val EXTRA_APP_SIGN = "appSign"
        const val EXTRA_USER_ID = "userID"
        const val EXTRA_USER_NAME = "userName"
        const val EXTRA_CALL_ID = "callID"
        const val EXTRA_IS_VOICE_CALL = "isVoiceCall"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)
        
        addFragment()
    }

    private fun addFragment() {
        // Get params from intent (or use BuildConfig defaults)
        val appID = intent.getLongExtra(EXTRA_APP_ID, BuildConfig.ZEGO_APP_ID)
        val appSign = intent.getStringExtra(EXTRA_APP_SIGN) ?: BuildConfig.ZEGO_APP_SIGN
        val userID = intent.getStringExtra(EXTRA_USER_ID) ?: return
        val userName = intent.getStringExtra(EXTRA_USER_NAME) ?: userID
        val callID = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val isVoiceOnly = intent.getBooleanExtra(EXTRA_IS_VOICE_CALL, false)

        // Create config (video or voice)
        val config = if (isVoiceOnly) {
            ZegoUIKitPrebuiltCallConfig.oneOnOneVoiceCall()
        } else {
            ZegoUIKitPrebuiltCallConfig.oneOnOneVideoCall()
        }

        // Create fragment
        val fragment = ZegoUIKitPrebuiltCallFragment.newInstance(
            appID, appSign, userID, userName, callID, config
        )

        // Add fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commitNow()
    }
}
