package com.turboguys.myaibot.data.repository

import com.turboguys.myaibot.data.api.GigaChatApi
import com.turboguys.myaibot.data.api.GigaChatOAuthApi
import com.turboguys.myaibot.data.api.dto.ChatMessage
import com.turboguys.myaibot.data.api.dto.GigaChatRequest
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

    suspend fun sendMessage(messages: List<Message>): Result<String> {
        return try {
            // Получаем токен доступа
            val tokenResult = getAccessToken()
            if (tokenResult.isFailure) {
                return Result.failure(tokenResult.exceptionOrNull() ?: Exception("Failed to get token"))
            }
            var accessToken = tokenResult.getOrNull() ?: return Result.failure(Exception("Empty token"))

            val chatMessages = messages.map { msg ->
                ChatMessage(
                    role = if (msg.isUser) "user" else "assistant",
                    content = msg.text
                )
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
                val answer = response.choices?.firstOrNull()?.message?.content
                    ?: throw Exception("Empty response")
                Result.success(answer)
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

