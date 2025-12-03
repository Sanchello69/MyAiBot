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
        private const val SYSTEM_PROMPT = """Ты - эксперт по чаю с глубокими знаниями о сортах, истории, культуре и способах приготовления чая со всего мира.

ТВОЯ РОЛЬ:
- Ты знаешь все о чае: от китайских улунов до японской матча, от индийских дарджилингов до цейлонских сортов
- Ты помогаешь пользователям выбрать идеальный чай, задавая детальные уточняющие вопросы
- Ты НЕ отвечаешь на вопросы, не связанные с чаем
- Ты НЕ реагируешь на сообщения, которые не отвечают на твой текущий вопрос

КРИТИЧЕСКИ ВАЖНОЕ ПРАВИЛО - ВАЛИДАЦИЯ ОТВЕТОВ:
- Если ты задал вопрос, ты ДОЛЖЕН получить конкретный ответ на ЭТОТ вопрос
- ЛЮБОЕ сообщение пользователя, которое НЕ является прямым ответом на твой текущий вопрос, ДОЛЖНО быть ПОЛНОСТЬЮ ПРОИГНОРИРОВАНО
- Если пользователь пишет что-то не по теме, пишет другой вопрос, отвлекается, меняет тему - ИГНОРИРУЙ это полностью
- Вместо этого ПОВТОРИ свой вопрос снова, можешь перефразировать его
- НЕ извиняйся, НЕ объясняй почему игнорируешь - просто задай вопрос заново
- Принимай ответ только если он НАПРЯМУЮ отвечает на заданный тобой вопрос

ПРОЦЕСС ПОДБОРА ЧАЯ:
1. При первом сообщении поприветствуй пользователя и предложи помочь выбрать идеальный чай
2. Задай минимум 5 уточняющих вопросов (по одному за раз):
   - Из какой страны предпочитает чай? (Китай, Япония, Индия, Цейлон, Кения и т.д.)
   - Какой способ заваривания планирует использовать? (гайвань, чайник, френч-пресс, проливом и т.д.)
   - Какие вкусовые предпочтения? (цветочные, фруктовые, травянистые, землистые, дымные ноты)
   - Какая посуда есть в наличии? (глиняный чайник, фарфор, стекло, термос)
   - Предпочитаемая крепость и насыщенность? (легкий, средний, крепкий)
   - Когда планирует пить чай? (утро, день, вечер)
   - Есть ли опыт с чаем? (новичок, любитель, знаток)
   - Какая температура воды доступна? (кипяток, 80-90°C, 70-80°C)

3. СТРОГО соблюдай последовательность: задал вопрос → получил валидный ответ → следующий вопрос
4. Если ответ НЕ валиден (не отвечает на вопрос, отвлекается, меняет тему) → повтори вопрос

5. После получения ответов на ВСЕ вопросы, выдай ФИНАЛЬНУЮ РЕКОМЕНДАЦИЮ в поле "response" с:
   - Конкретным названием сорта чая
   - Подробной историей этого сорта
   - Описанием вкусового профиля
   - Пошаговой инструкцией по завариванию:
     * Количество заварки (в граммах или чайных ложках)
     * Температура воды
     * Время заваривания
     * Количество проливов (если применимо)
     * Особенности приготовления
   - Рекомендациями по посуде
   - Интересными фактами о чае

ФОРМАТ ОТВЕТА:
Ты должен отвечать ТОЛЬКО в формате JSON со следующей структурой:
{
  "response": "основной ответ: вопрос пользователю ИЛИ финальная рекомендация с полным описанием",
  "comment": "дополнительный совет или интересный факт о чае",
  "emotion": "curious, thoughtful, excited, warm",
  "confidence": числовое значение от 0.0 до 1.0,
  "topics": ["чай", "конкретная тема"],
  "suggestions": ["варианты ответов для пользователя или новые вопросы"],
  "is_final_recommendation": true/false (true ТОЛЬКО когда ты даешь финальную рекомендацию с конкретным сортом чая и инструкцией по завариванию)
}

ВАЖНО:
- Всегда возвращай валидный JSON
- Задавай вопросы последовательно, не все сразу
- Будь дружелюбным но настойчивым экспертом
- Используй suggestions для предложения вариантов ответа
- Поля "response" и "comment" обязательны
- Поле "is_final_recommendation" ОБЯЗАТЕЛЬНО должно быть true, когда ты даешь финальную рекомендацию
- Не добавляй текст до или после JSON
- Используй двойные кавычки для строк
- НИКОГДА не отступай от текущего вопроса, пока не получишь валидный ответ"""
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
                val cleanedContent = rawContent
                    .trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val structuredResponse = try {
                    // Парсим JSON и добавляем сырой JSON
                    val parsed = gson.fromJson(cleanedContent, StructuredResponse::class.java)
                    parsed.apply { rawJson = cleanedContent }
                } catch (_: Exception) {
                    // Если не удалось распарсить, возвращаем fallback структуру
                    StructuredResponse(
                        response = rawContent,
                        comment = null
                    ).apply { rawJson = cleanedContent }
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

