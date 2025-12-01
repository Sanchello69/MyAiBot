package com.turboguys.myaibot.data.api.dto

import com.google.gson.annotations.SerializedName

data class OAuthRequest(
    @SerializedName("scope")
    val scope: String = "GIGACHAT_API_PERS"
)

