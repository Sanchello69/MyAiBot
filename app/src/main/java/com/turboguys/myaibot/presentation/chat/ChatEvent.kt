package com.turboguys.myaibot.presentation.chat

sealed class ChatEvent {
    data class SendMessage(val text: String) : ChatEvent()
    data class UpdateInputText(val text: String) : ChatEvent()
    object ClearError : ChatEvent()
}

