package com.example.chatapp.ui.qrcode

import android.content.pm.ActivityInfo
import android.os.Bundle
import com.journeyapps.barcodescanner.CaptureActivity

/**
 * Custom CaptureActivity that locks screen orientation to portrait
 */
class PortraitCaptureActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lock screen orientation to portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

