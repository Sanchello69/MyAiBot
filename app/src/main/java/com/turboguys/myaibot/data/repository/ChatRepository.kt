package com.turboguys.myaibot.data.repository

import com.google.gson.Gson
import com.turboguys.myaibot.data.api.GigaChatApi
import com.turboguys.myaibot.data.api.GigaChatOAuthApi
import com.turboguys.myaibot.data.api.dto.ChatMessage
import com.turboguys.myaibot.data.api.dto.GigaChatRequest
import com.turboguys.myaibot.data.api.dto.StructuredResponse
import com.turboguys.myaibot.domain.model.Message
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

class ChatRepository(
    private val api: GigaChatApi,
    private val oauthApi: GigaChatOAuthApi,
    private val authKey: String // Authorization key (Bearer token)
) {
    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0
    private var sessionId: String = UUID.randomUUID().toString()
    private val gson = Gson()

    companion object {
        private const val SYSTEM_PROMPT = """Ты должен отвечать ТОЛЬКО в формате JSON со следующей структурой:
{
  "response": "основной ответ на вопрос пользователя",
  "comment": "дополнительный комментарий или пояснение к ответу",
  "emotion": "эмоциональный тон ответа (например: neutral, happy, thoughtful, excited, curious)",
  "confidence": числовое значение от 0.0 до 1.0, показывающее уверенность в ответе,
  "topics": ["массив", "ключевых", "тем", "упомянутых", "в", "ответе"],
  "suggestions": ["массив", "предложений", "для", "продолжения", "диалога"]
}

ВАЖНО:
- Всегда возвращай валидный JSON
- Поля "response" и "comment" обязательны
- Остальные поля опциональны, но рекомендуются
- Не добавляй никакого текста до или после JSON
- Используй двойные кавычки для строк
- Не используй переносы строк внутри значений строк"""
    }

    private suspend fun getAccessToken(forceRefresh: Boolean = false): Result<String> {
        // Проверяем, не истек ли токен (оставляем запас 1 минута)
        if (!forceRefresh && cachedToken != null && System.currentTimeMillis() < tokenExpiresAt - 60000) {
            return Result.success(cachedToken!!)
        }

        return try {
            // RqUID должен быть в формате UUID
            val rqUid = UUID.randomUUID().toString()
            // Используем Bearer для авторизации
            val bearerAuth = "Bearer $authKey"
            
            val response = oauthApi.getAccessToken(
                authorization = bearerAuth,
                rqUid = rqUid
            )

            if (response.error != null) {
                Result.failure(Exception(response.errorDescription ?: response.error ?: "OAuth error"))
            } else {
                val token = response.accessToken
                    ?: throw Exception("Empty access token")
                
                cachedToken = token
                // Используем expires_at из ответа, если есть, иначе 30 минут по умолчанию
                tokenExpiresAt = if (response.expiresAt != null && response.expiresAt > 0) {
                    response.expiresAt * 1000 // конвертируем секунды в миллисекунды
                } else {
                    System.currentTimeMillis() + (30 * 60 * 1000) // 30 минут по умолчанию
                }
                
                Result.success(token)
            }
        } catch (e: HttpException) {
            val errorBody = try {
                e.response()?.errorBody()?.string() ?: ""
            } catch (ex: Exception) {
                ""
            }
            val errorMessage = if (errorBody.isNotEmpty()) {
                "HTTP ${e.code()}: $errorBody"
            } else {
                "HTTP ${e.code()}: ${e.message()}"
            }
            Result.failure(Exception("Ошибка авторизации (${e.code()}): $errorMessage"))
        } catch (e: IOException) {
            Result.failure(Exception("Ошибка сети при получении токена: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(messages: List<Message>): Result<StructuredResponse> {
        return try {
            // Получаем токен доступа
            val tokenResult = getAccessToken()
            if (tokenResult.isFailure) {
                return Result.failure(tokenResult.exceptionOrNull() ?: Exception("Failed to get token"))
            }
            var accessToken = tokenResult.getOrNull() ?: return Result.failure(Exception("Empty token"))

            // Добавляем системный промпт в начало списка сообщений
            val systemMessage = ChatMessage(
                role = "system",
                content = SYSTEM_PROMPT
            )

            val chatMessages = mutableListOf(systemMessage).apply {
                addAll(messages.map { msg ->
                    ChatMessage(
                        role = if (msg.isUser) "user" else "assistant",
                        content = msg.text
                    )
                })
            }

            val request = GigaChatRequest(
                messages = chatMessages
            )
            
            // Генерируем уникальные ID для каждого запроса
            val requestId = UUID.randomUUID().toString()
            
            // Пытаемся отправить запрос
            var response = try {
                api.chatCompletions(
                    authorization = "Bearer $accessToken",
                    requestId = requestId,
                    sessionId = sessionId,
                    request = request
                )
            } catch (e: HttpException) {
                // Если получили 401, обновляем токен и повторяем запрос
                if (e.code() == 401) {
                    val refreshResult = getAccessToken(forceRefresh = true)
                    if (refreshResult.isFailure) {
                        return Result.failure(refreshResult.exceptionOrNull() ?: Exception("Failed to refresh token"))
                    }
                    accessToken = refreshResult.getOrNull() ?: return Result.failure(Exception("Empty refreshed token"))
                    
                    // Повторяем запрос с новым токеном
                    api.chatCompletions(
                        authorization = "Bearer $accessToken",
                        requestId = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        request = request
                    )
                } else {
                    throw e
                }
            }
            
            if (response.error != null) {
                Result.failure(Exception(response.error.message ?: "Unknown error"))
            } else {
                val rawContent = response.choices?.firstOrNull()?.message?.content
                    ?: throw Exception("Empty response")

                // Пытаемся распарсить JSON ответ
                val structuredResponse = try {
                    // Убираем возможные markdown-маркеры кода
                    val cleanedContent = rawContent
                        .trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()

                    gson.fromJson(cleanedContent, StructuredResponse::class.java)
                } catch (e: Exception) {
                    // Если не удалось распарсить, возвращаем fallback структуру
                    StructuredResponse(
                        response = rawContent,
                        comment = null
                    )
                }

                // Проверяем, что есть хотя бы response
                if (structuredResponse.response.isNullOrBlank()) {
                    Result.failure(Exception("Empty response content"))
                } else {
                    Result.success(structuredResponse)
                }
            }
        } catch (e: HttpException) {
            val errorBody = try {
                e.response()?.errorBody()?.string() ?: ""
            } catch (ex: Exception) {
                ""
            }
            val errorMessage = if (errorBody.isNotEmpty()) {
                "HTTP ${e.code()}: $errorBody"
            } else {
                "HTTP ${e.code()}: ${e.message()}"
            }
            Result.failure(Exception(errorMessage))
        } catch (e: IOException) {
            Result.failure(Exception("Ошибка сети: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

