package com.turboguys.myaibot.data.api.dto

import com.google.gson.annotations.SerializedName

data class GigaChatRequest(
    @SerializedName("model")
    val model: String = "GigaChat",
    @SerializedName("messages")
    val messages: List<ChatMessage>,
    @SerializedName("stream")
    val stream: Boolean = false,
    @SerializedName("update_interval")
    val updateInterval: Int = 0,
    @SerializedName("temperature")
    val temperature: Double = 0.7,
    @SerializedName("max_tokens")
    val maxTokens: Int = 2000
)

data class ChatMessage(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: String
)

