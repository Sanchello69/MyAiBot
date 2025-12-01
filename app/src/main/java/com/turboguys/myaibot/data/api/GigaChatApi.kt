package com.turboguys.myaibot.data.api

import com.turboguys.myaibot.data.api.dto.GigaChatRequest
import com.turboguys.myaibot.data.api.dto.GigaChatResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GigaChatApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("X-Request-ID") requestId: String,
        @Header("X-Session-ID") sessionId: String,
        @Header("X-Client-ID") clientId: String = "android-app",
        @Body request: GigaChatRequest
    ): GigaChatResponse
}

