package com.turboguys.myaibot.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turboguys.myaibot.data.repository.ChatRepository
import com.turboguys.myaibot.domain.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    fun handleEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.SendMessage -> handleSendMessage(event.text)
            is ChatEvent.UpdateInputText -> handleUpdateInputText(event.text)
            is ChatEvent.ClearError -> handleClearError()
        }
    }

    private fun handleSendMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return

        val userMessage = Message(text = trimmedText, isUser = true)

        _state.update { currentState ->
            currentState.copy(
                messages = currentState.messages + userMessage,
                inputText = "",
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            val result = repository.sendMessage(_state.value.messages.filter { it.isUser || it.text.isNotBlank() })

            if (result.isSuccess) {
                val structuredResponse = result.getOrNull() ?: return@launch

                // Формируем основной текст ответа
                val responseText = buildString {
                    append(structuredResponse.response ?: "")
                    if (!structuredResponse.comment.isNullOrBlank()) {
                        append("\n\n💭 ${structuredResponse.comment}")
                    }
                    if (!structuredResponse.emotion.isNullOrBlank()) {
                        append("\n\n😊 Настроение: ${structuredResponse.emotion}")
                    }
                    if (structuredResponse.confidence != null) {
                        val confidencePercent = (structuredResponse.confidence * 100).toInt()
                        append("\n📊 Уверенность: $confidencePercent%")
                    }
                    if (!structuredResponse.topics.isNullOrEmpty()) {
                        append("\n\n🏷️ Темы: ${structuredResponse.topics.joinToString(", ")}")
                    }
                }

                val assistantMessage = Message(
                    text = responseText,
                    isUser = false,
                    suggestions = structuredResponse.suggestions,
                    emotion = structuredResponse.emotion,
                    confidence = structuredResponse.confidence,
                    topics = structuredResponse.topics,
                    comment = structuredResponse.comment,
                    rawJson = structuredResponse.rawJson
                )

                _state.update { currentState ->
                    currentState.copy(
                        messages = currentState.messages + assistantMessage,
                        isLoading = false
                    )
                }
            } else {
                val error = result.exceptionOrNull()
                _state.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = error?.message ?: "Произошла ошибка"
                    )
                }
            }
        }
    }

    private fun handleUpdateInputText(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    private fun handleClearError() {
        _state.update { it.copy(error = null) }
    }
}

