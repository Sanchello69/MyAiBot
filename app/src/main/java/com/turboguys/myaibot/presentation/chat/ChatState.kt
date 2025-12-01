package com.turboguys.myaibot.presentation.chat

import com.turboguys.myaibot.domain.model.Message

data class ChatState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val inputText: String = ""
)

