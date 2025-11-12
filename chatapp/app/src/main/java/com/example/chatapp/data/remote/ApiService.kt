package com.example.chatapp.data.remote

import com.example.chatapp.data.remote.model.LoginResponse
import com.example.chatapp.data.remote.model.ProfileUpdateRequest
import com.example.chatapp.data.remote.model.RegisterRequest
import com.example.chatapp.data.remote.model.RegisterResponse
import com.example.chatapp.data.remote.model.UserDto
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
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path


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
}


