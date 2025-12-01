package com.turboguys.myaibot.presentation.chat

sealed class ChatAction {
    data class SendMessage(val text: String) : ChatAction()
    data class MessageReceived(val text: String) : ChatAction()
    data class ErrorOccurred(val error: String) : ChatAction()
    object ClearError : ChatAction()
}

