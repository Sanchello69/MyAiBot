package com.turboguys.myaibot.data.api.dto

import com.google.gson.annotations.SerializedName

data class GigaChatResponse(
    @SerializedName("choices")
    val choices: List<Choice>?,
    @SerializedName("error")
    val error: ErrorResponse?
)

data class Choice(
    @SerializedName("message")
    val message: ChatMessage,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class ErrorResponse(
    @SerializedName("message")
    val message: String?,
    @SerializedName("type")
    val type: String?
)

