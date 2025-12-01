package com.turboguys.myaibot.data.api

import com.turboguys.myaibot.data.api.dto.OAuthResponse
import retrofit2.http.*

interface GigaChatOAuthApi {
    @FormUrlEncoded
    @POST("v2/oauth")
    @Headers("Content-Type: application/x-www-form-urlencoded", "Accept: application/json")
    suspend fun getAccessToken(
        @Header("Authorization") authorization: String,
        @Header("RqUID") rqUid: String,
        @Field("scope") scope: String = "GIGACHAT_API_PERS"
    ): OAuthResponse
}

