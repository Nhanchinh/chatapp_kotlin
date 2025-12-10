package com.example.chatapp.data.remote.model

import com.squareup.moshi.Json

data class ZegoTokenRequest(
    @Json(name = "room_id") val roomId: String? = "",
    @Json(name = "expiry_seconds") val expirySeconds: Int = 3600
)

data class ZegoTokenResponse(
    @Json(name = "token") val token: String,
    @Json(name = "app_id") val appId: Long,
    @Json(name = "expire_at") val expireAt: Long
)

