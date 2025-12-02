package com.turboguys.myaibot.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * Структурированный ответ от GigaChat в JSON формате
 */
data class StructuredResponse(
    @SerializedName("response")
    val response: String? = null,

    @SerializedName("comment")
    val comment: String? = null,

    @SerializedName("emotion")
    val emotion: String? = null,

    @SerializedName("confidence")
    val confidence: Double? = null,

    @SerializedName("topics")
    val topics: List<String>? = null,

    @SerializedName("suggestions")
    val suggestions: List<String>? = null
) {
    // Сырой JSON (заполняется после парсинга)
    var rawJson: String? = null
}