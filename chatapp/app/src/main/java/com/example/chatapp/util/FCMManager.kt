package com.example.chatapp.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.chatapp.data.remote.ApiService
import com.example.chatapp.data.remote.model.FCMTokenRequest
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FCMManager(
    private val context: Context,
    private val apiService: ApiService
) {
    companion object {
        private const val TAG = "FCMManager"
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "fcm_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_TOKEN_SENT = "token_sent_to_server"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not needed for older versions
        }
    }

    /**
     * Request notification permission (Android 13+)
     */
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission()) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    /**
     * Get FCM token from Firebase and send to backend
     */
    fun getFCMTokenAndSendToServer(authToken: String) {
        Log.d(TAG, "🔄 Starting FCM token retrieval process...")
        Log.d(TAG, "   Auth token: ${authToken.take(20)}...")
        
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                Log.d(TAG, "📡 Firebase token task completed. Success: ${task.isSuccessful}")
                
                if (!task.isSuccessful) {
                    Log.e(TAG, "❌ Fetching FCM registration token failed", task.exception)
                    task.exception?.printStackTrace()
                    return@OnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result
                Log.d(TAG, "✅ FCM Token received: ${token?.take(50)}...")
                
                if (token.isNullOrEmpty()) {
                    Log.e(TAG, "❌ FCM token is null or empty!")
                    return@OnCompleteListener
                }

                // Save token locally
                saveFCMToken(token)
                Log.d(TAG, "💾 FCM token saved locally")

                // Send to backend
                Log.d(TAG, "📤 Sending FCM token to backend...")
                sendTokenToServer(token, authToken)
            }).addOnFailureListener { e ->
                Log.e(TAG, "❌ Fatal error getting FCM token", e)
                e.printStackTrace()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in getFCMTokenAndSendToServer", e)
            e.printStackTrace()
        }
    }

    /**
     * Save FCM token to SharedPreferences
     */
    private fun saveFCMToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    /**
     * Get saved FCM token from SharedPreferences
     */
    fun getSavedFCMToken(): String? {
        return prefs.getString(KEY_FCM_TOKEN, null)
    }

    /**
     * Send FCM token to backend server
     */
    fun sendTokenToServer(fcmToken: String, authToken: String) {
        Log.d(TAG, "🚀 sendTokenToServer called")
        Log.d(TAG, "   FCM Token: ${fcmToken.take(50)}...")
        Log.d(TAG, "   Auth Token: ${authToken.take(20)}...")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "📱 Getting device ID...")
                val deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
                Log.d(TAG, "   Device ID: $deviceId")

                Log.d(TAG, "📦 Creating FCM token request...")
                val request = FCMTokenRequest(
                    fcmToken = fcmToken,
                    deviceId = deviceId,
                    deviceType = "android"
                )
                Log.d(TAG, "   Request created successfully")

                Log.d(TAG, "🌐 Calling API registerFCMToken...")
                val response = apiService.registerFCMToken(
                    token = "Bearer $authToken",
                    request = request
                )
                Log.d(TAG, "📨 API response received")
                Log.d(TAG, "   Success: ${response.success}")
                Log.d(TAG, "   Message: ${response.message}")

                if (response.success) {
                    Log.d(TAG, "✅ FCM token sent to server successfully")
                    markTokenAsSent()
                } else {
                    Log.e(TAG, "❌ Failed to send FCM token to server: ${response.message}")
                }
            } catch (e: retrofit2.HttpException) {
                Log.e(TAG, "❌ HTTP Error sending FCM token", e)
                Log.e(TAG, "   Status code: ${e.code()}")
                Log.e(TAG, "   Error body: ${e.response()?.errorBody()?.string()}")
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "❌ Network error: Cannot reach server", e)
                Log.e(TAG, "   Check if backend is running and reachable")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sending FCM token to server", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * Mark token as sent to server
     */
    private fun markTokenAsSent() {
        prefs.edit().putBoolean(KEY_TOKEN_SENT, true).apply()
    }

    /**
     * Check if token was already sent to server
     */
    fun isTokenSentToServer(): Boolean {
        return prefs.getBoolean(KEY_TOKEN_SENT, false)
    }

    /**
     * Clear token sent flag (e.g., on logout)
     */
    fun clearTokenSentFlag() {
        prefs.edit().putBoolean(KEY_TOKEN_SENT, false).apply()
    }

    /**
     * Deactivate FCM token on logout
     */
    suspend fun deactivateToken(authToken: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val fcmToken = getSavedFCMToken() ?: return@withContext false
                
                val response = apiService.deactivateFCMToken(
                    token = "Bearer $authToken",
                    fcmToken = fcmToken
                )
                
                if (response.success) {
                    Log.d(TAG, "✅ FCM token deactivated successfully")
                    clearTokenSentFlag()
                    true
                } else {
                    Log.e(TAG, "❌ Failed to deactivate FCM token")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error deactivating FCM token", e)
                false
            }
        }
    }
}

