package com.turboguys.myaibot.data.api.dto

import com.google.gson.annotations.SerializedName

data class OAuthResponse(
    @SerializedName("access_token")
    val accessToken: String?,
    @SerializedName("expires_at")
    val expiresAt: Long?,
    @SerializedName("error")
    val error: String?,
    @SerializedName("error_description")
    val errorDescription: String?
)

