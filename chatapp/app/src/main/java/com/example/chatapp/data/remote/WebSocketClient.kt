package com.example.chatapp.data.remote

import com.example.chatapp.BuildConfig
import com.example.chatapp.data.remote.model.WebSocketMessage
import com.example.chatapp.data.remote.model.WebSocketAckResponse
import com.example.chatapp.data.remote.model.WebSocketMessageResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebSocketClient {
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private var webSocket: WebSocket? = null
    private var currentUserId: String? = null
    private var currentToken: String? = null

    private val client = OkHttpClient.Builder()
        .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun connect(userId: String, token: String, resumeSince: Long? = null): Flow<WebSocketEvent> = callbackFlow {
        currentUserId = userId
        currentToken = token

        val baseHttpUrl = BuildConfig.BASE_URL.trimEnd('/').toHttpUrlOrNull()
            ?: throw IllegalStateException("Invalid base URL")

        val httpUrl = baseHttpUrl.newBuilder()
            .addPathSegments("messages/ws/chat/$userId")
            .addQueryParameter("token", token)
            .apply {
                resumeSince?.let { addQueryParameter("resume_since", it.toString()) }
            }
            .build()

        val wsUrl = if (baseHttpUrl.isHttps) {
            httpUrl.toString().replaceFirst("https://", "wss://")
        } else {
            httpUrl.toString().replaceFirst("http://", "ws://")
        }

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        val wsListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@WebSocketClient.webSocket = webSocket
                trySend(WebSocketEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    // Try to parse as ACK first
                    val ackAdapter = moshi.adapter(WebSocketAckResponse::class.java)
                    val ackResponse = ackAdapter.fromJson(text)
                    if (ackResponse != null) {
                        trySend(WebSocketEvent.MessageAck(ackResponse.ack))
                        return
                    }

                    // Try to parse as message
                    val messageAdapter = moshi.adapter(WebSocketMessageResponse::class.java)
                    val messageResponse = messageAdapter.fromJson(text)
                    if (messageResponse != null) {
                        trySend(WebSocketEvent.NewMessage(messageResponse))
                        return
                    }

                    // Try to parse as generic message (typing, delivered, seen)
                    val genericAdapter = moshi.adapter(WebSocketMessage::class.java)
                    val genericMessage = genericAdapter.fromJson(text)
                    if (genericMessage != null) {
                        when (genericMessage.type) {
                            "typing_start" -> trySend(WebSocketEvent.TypingStarted(genericMessage.from ?: ""))
                            "typing_stop" -> trySend(WebSocketEvent.TypingStopped(genericMessage.from ?: ""))
                            "delivered" -> trySend(WebSocketEvent.MessageDelivered(genericMessage.messageId ?: ""))
                            "seen" -> trySend(WebSocketEvent.MessageSeen(genericMessage.messageId ?: ""))
                            else -> trySend(WebSocketEvent.RawMessage(text))
                        }
                        return
                    }

                    // Fallback to raw message
                    trySend(WebSocketEvent.RawMessage(text))
                } catch (e: Exception) {
                    trySend(WebSocketEvent.Error(e.message ?: "Failed to parse message"))
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                trySend(WebSocketEvent.Error("Binary messages not supported"))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                trySend(WebSocketEvent.Closing(code, reason))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySend(WebSocketEvent.Closed(code, reason))
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(WebSocketEvent.Error(t.message ?: "WebSocket connection failed"))
                close()
            }
        }

        val ws = client.newWebSocket(request, wsListener)
        this@WebSocketClient.webSocket = ws

        awaitClose {
            ws.close(1000, "Client closing")
            this@WebSocketClient.webSocket = null
        }
    }

    suspend fun sendMessage(from: String, to: String, content: String, clientMessageId: String? = null): Result<Unit> {
        return suspendCancellableCoroutine { continuation ->
            val message = WebSocketMessage(
                from = from,
                to = to,
                content = content,
                clientMessageId = clientMessageId ?: UUID.randomUUID().toString()
            )

            val messageAdapter = moshi.adapter(WebSocketMessage::class.java)
            val json = messageAdapter.toJson(message)

            val ws = webSocket
            if (ws == null) {
                continuation.resumeWithException(IllegalStateException("WebSocket not connected"))
                return@suspendCancellableCoroutine
            }

            val sent = ws.send(json)
            if (sent) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resumeWithException(IllegalStateException("Failed to send message"))
            }
        }
    }

    suspend fun sendTyping(from: String, to: String, isTyping: Boolean): Result<Unit> {
        return suspendCancellableCoroutine { continuation ->
            val message = WebSocketMessage(
                from = from,
                to = to,
                type = if (isTyping) "typing_start" else "typing_stop"
            )

            val messageAdapter = moshi.adapter(WebSocketMessage::class.java)
            val json = messageAdapter.toJson(message)

            val ws = webSocket
            if (ws == null) {
                continuation.resumeWithException(IllegalStateException("WebSocket not connected"))
                return@suspendCancellableCoroutine
            }

            val sent = ws.send(json)
            if (sent) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resumeWithException(IllegalStateException("Failed to send typing indicator"))
            }
        }
    }

    suspend fun sendDelivered(messageId: String, conversationId: String, from: String, to: String): Result<Unit> {
        return suspendCancellableCoroutine { continuation ->
            val message = WebSocketMessage(
                type = "delivered",
                messageId = messageId,
                conversationId = conversationId,
                from = from,
                to = to
            )

            val messageAdapter = moshi.adapter(WebSocketMessage::class.java)
            val json = messageAdapter.toJson(message)

            val ws = webSocket
            if (ws == null) {
                continuation.resumeWithException(IllegalStateException("WebSocket not connected"))
                return@suspendCancellableCoroutine
            }

            val sent = ws.send(json)
            if (sent) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resumeWithException(IllegalStateException("Failed to send delivered status"))
            }
        }
    }

    suspend fun sendSeen(messageId: String, conversationId: String, from: String, to: String): Result<Unit> {
        return suspendCancellableCoroutine { continuation ->
            val message = WebSocketMessage(
                type = "seen",
                messageId = messageId,
                conversationId = conversationId,
                from = from,
                to = to
            )

            val messageAdapter = moshi.adapter(WebSocketMessage::class.java)
            val json = messageAdapter.toJson(message)

            val ws = webSocket
            if (ws == null) {
                continuation.resumeWithException(IllegalStateException("WebSocket not connected"))
                return@suspendCancellableCoroutine
            }

            val sent = ws.send(json)
            if (sent) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resumeWithException(IllegalStateException("Failed to send seen status"))
            }
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        currentUserId = null
        currentToken = null
    }

    fun isConnected(): Boolean {
        return webSocket != null
    }
}

sealed class WebSocketEvent {
    object Connected : WebSocketEvent()
    data class MessageAck(val ack: com.example.chatapp.data.remote.model.MessageAck) : WebSocketEvent()
    data class NewMessage(val message: com.example.chatapp.data.remote.model.WebSocketMessageResponse) : WebSocketEvent()
    data class TypingStarted(val userId: String) : WebSocketEvent()
    data class TypingStopped(val userId: String) : WebSocketEvent()
    data class MessageDelivered(val messageId: String) : WebSocketEvent()
    data class MessageSeen(val messageId: String) : WebSocketEvent()
    data class RawMessage(val text: String) : WebSocketEvent()
    data class Error(val message: String) : WebSocketEvent()
    data class Closing(val code: Int, val reason: String) : WebSocketEvent()
    data class Closed(val code: Int, val reason: String) : WebSocketEvent()
}

