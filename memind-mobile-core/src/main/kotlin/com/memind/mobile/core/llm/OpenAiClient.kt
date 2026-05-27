package com.memind.mobile.core.llm

import com.memind.mobile.core.store.MemoryItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

public class OpenAiClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com",
    private val chatModel: String = "gpt-4o-mini",
    private val embeddingModel: String = "text-embedding-3-small",
) : ChatClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun chat(
        prompt: String,
        systemMessage: String?,
    ): ChatResponse {
        val body = buildChatRequestBody(prompt, systemMessage)
        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody(jsonMedia))
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: throw RuntimeException("Empty response")
        val parsed = json.decodeFromString<ChatCompletionResponse>(bodyStr)

        return ChatResponse(
            content = parsed.choices.firstOrNull()?.message?.content ?: "",
            model = parsed.model,
        )
    }

    override suspend fun embed(text: String): EmbeddingResponse {
        val body = buildEmbeddingRequestBody(text)
        val request = Request.Builder()
            .url("$baseUrl/v1/embeddings")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody(jsonMedia))
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: throw RuntimeException("Empty response")
        val parsed = json.decodeFromString<EmbeddingResponse>(bodyStr)

        return EmbeddingResponse(
            embedding = parsed.data.firstOrNull()?.embedding ?: emptyList(),
            model = parsed.model,
        )
    }

    override suspend fun health(): Boolean {
        return try {
            val response = chat("Say 'ok'", null)
            response.content.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    private fun buildChatRequestBody(prompt: String, system: String?): String {
        val messages = buildList {
            system?.let { add(ChatMessage("system", it)) }
            add(ChatMessage("user", prompt))
        }
        return """{
            "model": "$chatModel",
            "messages": [${messages.joinToString(",") { """{"role":"${it.role}","content":"${it.content}"}""" }}]
        }""".trimIndent()
    }

    private fun buildEmbeddingRequestBody(text: String): String {
        return """{"input": "$text", "model": "$embeddingModel"}""".trimIndent()
    }
}

@Serializable
private data class ChatCompletionResponse(
    val choices: List<Choice>,
    val model: String,
)

@Serializable
private data class Choice(
    val message: ChatMessage,
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String,
)