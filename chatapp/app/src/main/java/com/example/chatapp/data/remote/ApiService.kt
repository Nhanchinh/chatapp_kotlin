package com.example.chatapp.data.remote

import com.example.chatapp.data.remote.model.LoginResponse
import com.example.chatapp.data.remote.model.RegisterRequest
import com.example.chatapp.data.remote.model.RegisterResponse
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface ApiService {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): LoginResponse
}


