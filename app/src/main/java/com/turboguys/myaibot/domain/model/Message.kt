package com.turboguys.myaibot.domain.model

data class Message(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val suggestions: List<String>? = null,
    val emotion: String? = null,
    val confidence: Double? = null,
    val topics: List<String>? = null,
    val comment: String? = null,
    val rawJson: String? = null,
    val isFinalRecommendation: Boolean = false
)

