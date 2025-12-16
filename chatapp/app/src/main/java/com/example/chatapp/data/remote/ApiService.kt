package com.example.chatapp.data.remote

import com.example.chatapp.data.remote.model.LoginResponse
import com.example.chatapp.data.remote.model.ProfileUpdateRequest
import com.example.chatapp.data.remote.model.RegisterRequest
import com.example.chatapp.data.remote.model.RegisterResponse
import com.example.chatapp.data.remote.model.UserDto
import com.example.chatapp.data.remote.model.OtpRequest
import com.example.chatapp.data.remote.model.SimpleMessageResponse
import com.example.chatapp.data.remote.model.VerifyOtpRequest
import com.example.chatapp.data.remote.model.VerifyOtpResponse
import com.example.chatapp.data.remote.model.ResetPasswordRequest
import com.example.chatapp.data.remote.model.ChangePasswordRequest
import com.example.chatapp.data.remote.model.SearchUsersResponse
import com.example.chatapp.data.remote.model.FriendActionResponse
import com.example.chatapp.data.remote.model.FriendsListResponse
import com.example.chatapp.data.remote.model.FriendRequestsResponse
import com.example.chatapp.data.remote.model.ConversationsResponse
import com.example.chatapp.data.remote.model.MessagesResponse
import com.example.chatapp.data.remote.model.UnreadMessagesResponse
import com.example.chatapp.data.remote.model.MarkReadRequest
import com.example.chatapp.data.remote.model.MarkReadResponse
import com.example.chatapp.data.remote.model.PresenceResponse
import com.example.chatapp.data.remote.model.BatchPresenceResponse
import com.example.chatapp.data.remote.model.RefreshTokenRequest
import com.example.chatapp.data.remote.model.RefreshTokenResponse
import com.example.chatapp.data.remote.model.PublicKeysResponse
import com.example.chatapp.data.remote.model.StoreKeysRequest
import com.example.chatapp.data.remote.model.StoreKeysResponse
import com.example.chatapp.data.remote.model.GetKeyResponse
import com.example.chatapp.data.remote.model.CreateConversationRequest
import com.example.chatapp.data.remote.model.CreateConversationResponse
import com.example.chatapp.data.remote.model.CreateGroupRequest
import com.example.chatapp.data.remote.model.CreateGroupResponse
import com.example.chatapp.data.remote.model.GroupInfoResponse
import com.example.chatapp.data.remote.model.MediaDownloadResponse
import com.example.chatapp.data.remote.model.AddMembersRequest
import com.example.chatapp.data.remote.model.MediaUploadRequest
import com.example.chatapp.data.remote.model.MediaUploadResponse
import com.example.chatapp.data.remote.model.FCMTokenRequest
import com.example.chatapp.data.remote.model.FCMTokenResponse
import com.example.chatapp.data.remote.model.ZegoTokenRequest
import com.example.chatapp.data.remote.model.ZegoTokenResponse
import com.example.chatapp.data.remote.model.NotificationsResponse
import com.example.chatapp.data.remote.model.NotificationCountResponse
import com.example.chatapp.data.remote.model.NotificationActionResponse
import com.example.chatapp.data.remote.model.CreateBackupRequest
import com.example.chatapp.data.remote.model.GetBackupResponse
import com.example.chatapp.data.remote.model.BackupExistsResponse
import com.squareup.moshi.Json
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Path

// Avatar Upload Response
data class AvatarUploadResponse(
    val avatar: String  // Relative path: /static/avatars/xxx.jpg
)

// Reactions API Models
data class ReactRequest(
    val emoji: String
)

data class ReactResponse(
    @Json(name = "message_id") val messageId: String,
    val reactions: Map<String, String> = emptyMap()
)

interface ApiService {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): LoginResponse

    @POST("auth/refresh")
    suspend fun refreshTokens(
        @Body body: RefreshTokenRequest
    ): RefreshTokenResponse

    @GET("auth/profile")
    suspend fun getProfile(@Header("Authorization") token: String): UserDto

    @PATCH("auth/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body body: ProfileUpdateRequest
    ): UserDto

    // Avatar Upload API
    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadAvatar(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): AvatarUploadResponse

    @GET("users/search")
    suspend fun searchUsers(
        @Header("Authorization") token: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
        @Query("prefix") prefix: Boolean = false
    ): SearchUsersResponse

    @GET("users/{user_id}")
    suspend fun getUserById(
        @Header("Authorization") token: String,
        @Path("user_id") userId: String
    ): UserDto

    @POST("friends/request/{target_user_id}")
    suspend fun sendFriendRequest(
        @Header("Authorization") token: String,
        @Path("target_user_id") targetUserId: String
    ): FriendActionResponse

    @GET("friends/list")
    suspend fun getFriendsList(
        @Header("Authorization") token: String
    ): FriendsListResponse

    @GET("friends/requests")
    suspend fun getFriendRequests(
        @Header("Authorization") token: String
    ): FriendRequestsResponse

    @POST("friends/accept/{from_user_id}")
    suspend fun acceptFriendRequest(
        @Header("Authorization") token: String,
        @Path("from_user_id") fromUserId: String
    ): FriendActionResponse

    @DELETE("friends/request/{user_id}")
    suspend fun cancelOrDeclineFriendRequest(
        @Header("Authorization") token: String,
        @Path("user_id") userId: String
    ): FriendActionResponse

    @DELETE("friends/{friend_id}")
    suspend fun unfriend(
        @Header("Authorization") token: String,
        @Path("friend_id") friendId: String
    ): FriendActionResponse

    // Chat API
    @POST("conversations")
    suspend fun createConversation(
        @Header("Authorization") token: String,
        @Body body: CreateConversationRequest
    ): CreateConversationResponse

    @GET("conversations")
    suspend fun getConversations(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null
    ): ConversationsResponse

    @GET("conversations/{conversation_id}/messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("conversation_id") conversationId: String,
        @Query("limit") limit: Int = 50,
        @Query("cursor") cursor: String? = null
    ): MessagesResponse

    @GET("messages/unread")
    suspend fun getUnreadMessages(
        @Header("Authorization") token: String,
        @Query("from_user_id") fromUserId: String? = null
    ): UnreadMessagesResponse

    @POST("messages/mark_read")
    suspend fun markRead(
        @Header("Authorization") token: String,
        @Body body: MarkReadRequest
    ): MarkReadResponse

    @DELETE("conversations/{conversation_id}")
    suspend fun deleteConversation(
        @Header("Authorization") token: String,
        @Path("conversation_id") conversationId: String
    ): FriendActionResponse

    @DELETE("messages/{message_id}")
    suspend fun deleteMessage(
        @Header("Authorization") token: String,
        @Path("message_id") messageId: String
    ): FriendActionResponse

    @POST("messages/{message_id}/react")
    suspend fun reactToMessage(
        @Header("Authorization") token: String,
        @Path("message_id") messageId: String,
        @Body body: ReactRequest
    ): ReactResponse

    // Presence API
    @GET("presence/{user_id}")
    suspend fun getPresence(
        @Header("Authorization") token: String,
        @Path("user_id") userId: String
    ): PresenceResponse

    @GET("presence/batch")
    suspend fun getBatchPresence(
        @Header("Authorization") token: String,
        @Query("user_ids") userIds: String
    ): BatchPresenceResponse

    @POST("presence/offline")
    suspend fun setOffline(
        @Header("Authorization") token: String
    ): FriendActionResponse

    // Password & OTP
    @POST("auth/request-otp")
    suspend fun requestPasswordOtp(
        @Body body: OtpRequest
    ): SimpleMessageResponse

    @POST("auth/verify-otp")
    suspend fun verifyPasswordOtp(
        @Body body: VerifyOtpRequest
    ): VerifyOtpResponse

    @POST("auth/reset-password")
    suspend fun resetPassword(
        @Body body: ResetPasswordRequest
    ): SimpleMessageResponse

    @POST("auth/change-password")
    suspend fun changePassword(
        @Header("Authorization") token: String,
        @Body body: ChangePasswordRequest
    ): SimpleMessageResponse

    // Media API
    @POST("media")
    suspend fun uploadMedia(
        @Header("Authorization") token: String,
        @Body body: MediaUploadRequest
    ): MediaUploadResponse

    @GET("media/{media_id}")
    suspend fun downloadMedia(
        @Header("Authorization") token: String,
        @Path("media_id") mediaId: String
    ): MediaDownloadResponse

    // E2EE Key Exchange API
    @GET("users/public-keys/batch")
    suspend fun getPublicKeys(
        @Header("Authorization") token: String,
        @Query("user_ids") userIds: String  // Comma-separated user IDs
    ): PublicKeysResponse

    @POST("conversation-keys/store")
    suspend fun storeConversationKeys(
        @Header("Authorization") token: String,
        @Body body: StoreKeysRequest
    ): StoreKeysResponse

    @GET("conversation-keys/{conversation_id}/my-key")
    suspend fun getMyConversationKey(
        @Header("Authorization") token: String,
        @Path("conversation_id") conversationId: String
    ): GetKeyResponse
    
    @DELETE("conversation-keys/{conversation_id}/my-key")
    suspend fun deleteMyConversationKey(
        @Header("Authorization") token: String,
        @Path("conversation_id") conversationId: String
    ): SimpleMessageResponse

    // Groups
    @POST("conversations/groups")
    suspend fun createGroup(
        @Header("Authorization") token: String,
        @Body body: CreateGroupRequest
    ): CreateGroupResponse

    @POST("conversations/groups/{conversation_id}/members")
    suspend fun addGroupMembers(
        @Header("Authorization") token: String,
        @Path("conversation_id") conversationId: String,
        @Body body: AddMembersRequest
    ): GroupInfoResponse

    @GET("conversations/groups/{conversation_id}")
    suspend fun getGroupInfo(
        @Header("Authorization") token: String,
        @Path("conversation_id") conversationId: String
    ): GroupInfoResponse

    @DELETE("conversations/groups/{conversation_id}/members/{member_id}")
    suspend fun removeGroupMember(
        @Header("Authorization") token: String,
        @Path("conversation_id") conversationId: String,
        @Path("member_id") memberId: String
    ): GroupInfoResponse

    @POST("conversations/groups/{conversation_id}/leave")
    suspend fun leaveGroup(
        @Header("Authorization") token: String,
        @Path("conversation_id") conversationId: String
    ): GroupInfoResponse

    // FCM Token endpoints
    @POST("fcm/token")
    suspend fun registerFCMToken(
        @Header("Authorization") token: String,
        @Body request: FCMTokenRequest
    ): FCMTokenResponse

    @DELETE("fcm/token")
    suspend fun deactivateFCMToken(
        @Header("Authorization") token: String,
        @Query("fcm_token") fcmToken: String
    ): FCMTokenResponse

    // Zego Token
    @POST("zego/token")
    suspend fun getZegoToken(
        @Header("Authorization") authorization: String,
        @Body body: ZegoTokenRequest
    ): ZegoTokenResponse

    // Notifications API
    @GET("notifications")
    suspend fun getNotifications(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 50,
        @Query("cursor") cursor: String? = null
    ): NotificationsResponse

    @POST("notifications/{notification_id}/read")
    suspend fun markNotificationRead(
        @Header("Authorization") token: String,
        @Path("notification_id") notificationId: String
    ): NotificationActionResponse

    @POST("notifications/read_all")
    suspend fun markAllNotificationsRead(
        @Header("Authorization") token: String
    ): NotificationActionResponse

    @DELETE("notifications/{notification_id}")
    suspend fun deleteNotification(
        @Header("Authorization") token: String,
        @Path("notification_id") notificationId: String
    ): NotificationActionResponse

    @GET("notifications/unread/count")
    suspend fun getUnreadNotificationCount(
        @Header("Authorization") token: String
    ): NotificationCountResponse

    // Key Backup API
    @POST("key-backup")
    suspend fun createKeyBackup(
        @Header("Authorization") token: String,
        @Body request: CreateBackupRequest
    ): SimpleMessageResponse

    @GET("key-backup")
    suspend fun getKeyBackup(
        @Header("Authorization") token: String
    ): GetBackupResponse

    @GET("key-backup/exists")
    suspend fun checkKeyBackupExists(
        @Header("Authorization") token: String
    ): BackupExistsResponse

    @DELETE("key-backup")
    suspend fun deleteKeyBackup(
        @Header("Authorization") token: String
    ): SimpleMessageResponse

}


