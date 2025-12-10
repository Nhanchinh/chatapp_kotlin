package com.example.chatapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.chatapp.MainActivity
import com.example.chatapp.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "FCMService"
        const val CHANNEL_ID = "chat_messages"
        const val CHANNEL_NAME = "Chat Messages"
        
        // Listener for token updates
        var onNewToken: ((String) -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Called when a new FCM token is generated
     * This happens on first app install, after re-install, or when token is refreshed
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "✅ New FCM token: $token")
        
        // Notify listener (if app is running)
        onNewToken?.invoke(token)
        
        // Store token locally and send to backend
        serviceScope.launch {
            try {
                // Save token to SharedPreferences
                val prefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("fcm_token", token).apply()
                
                // TODO: Send token to backend via API
                // This will be handled by MainActivity or AuthViewModel
                Log.d(TAG, "FCM token saved locally")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to save FCM token", e)
            }
        }
    }

    /**
     * Called when a message is received
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d(TAG, "📩 Message received from: ${message.from}")
        
        // Check if message contains a notification payload
        message.notification?.let { notification ->
            Log.d(TAG, "Notification title: ${notification.title}")
            Log.d(TAG, "Notification body: ${notification.body}")
            
            // Check if message is encrypted and replace body if needed
            val isEncryptedStr = message.data["is_encrypted"]?.lowercase()
            val isEncrypted = when (isEncryptedStr) {
                "true" -> true
                else -> false
            }
            
            val displayBody = if (isEncrypted) {
                "đã gửi tin nhắn cho bạn"
            } else {
                notification.body ?: ""
            }
            
            showNotification(
                title = notification.title ?: "New Message",
                body = displayBody,
                data = message.data
            )
        }
        
        // Check if message contains a data payload (but no notification payload)
        if (message.data.isNotEmpty() && message.notification == null) {
            Log.d(TAG, "Message data: ${message.data}")
            
            val type = message.data["type"]
            if (type == "chat_message") {
                val senderName = message.data["sender_name"] ?: "Someone"
                val messageContent = message.data["content"] ?: "New message"
                
                // Check if message is encrypted
                val isEncryptedStr = message.data["is_encrypted"]?.lowercase()
                val isEncrypted = when (isEncryptedStr) {
                    "true" -> true
                    else -> false
                }
                
                val displayBody = if (isEncrypted) {
                    "đã gửi tin nhắn cho bạn"
                } else {
                    messageContent
                }
                
                showNotification(
                    title = senderName,
                    body = displayBody,
                    data = message.data
                )
            }
        }
    }

    /**
     * Show notification to user
     */
    private fun showNotification(
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        // Check if message is encrypted
        val isEncryptedStr = data["is_encrypted"]?.lowercase()
        val isEncrypted = when (isEncryptedStr) {
            "true" -> true
            else -> false
        }
        
        // If message is encrypted, replace body with placeholder text
        val displayBody = if (isEncrypted) {
            "đã gửi tin nhắn cho bạn"
        } else {
            body
        }
        
        Log.d(TAG, "📩 Creating notification: title=$title, body=$displayBody, isEncrypted=$isEncrypted")
        
        // Create intent to open app when notification is tapped
        // Just open MainActivity without any navigation data - user can navigate manually
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // No extra data - just open the app
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your app icon
            .setContentTitle(title)
            .setContentText(displayBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Use conversation ID from data as notification ID to update existing notification
        val conversationId = data["conversation_id"]
        val notificationId = conversationId?.hashCode() ?: System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    /**
     * Create notification channel (required for Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new chat messages"
                enableVibration(true)
                setShowBadge(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onNewToken = null
    }
}

