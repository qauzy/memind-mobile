package com.memind.mobile.core.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
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

    /**
     * 调用 OpenAI 兼容聊天接口。
     *
     * prompt 是用户输入，systemMessage 可选；返回模型文本和模型名。
     */
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

    /**
     * 调用 OpenAI 兼容 embedding 接口。
     *
     * 返回文本向量，供后续向量检索和语义去重使用。
     */
    override suspend fun embed(text: String): EmbeddingResponse {
        val body = buildEmbeddingRequestBody(text)
        val request = Request.Builder()
            .url("$baseUrl/v1/embeddings")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody(jsonMedia))
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: throw RuntimeException("Empty response")
        val parsed = json.decodeFromString<EmbeddingApiResponse>(bodyStr)

        return EmbeddingResponse(
            embedding = parsed.data.firstOrNull()?.embedding ?: emptyList(),
            model = parsed.model,
        )
    }

    /**
     * 检查远程 LLM 能力是否可用。
     *
     * 通过一次极小聊天请求判断客户端是否能正常响应。
     */
    override suspend fun health(): Boolean {
        return try {
            val response = chat("Say 'ok'", null)
            response.content.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 构建聊天请求 JSON。
     *
     * 使用 kotlinx.serialization 生成 JSON，避免手写字符串时破坏引号或换行转义。
     */
    private fun buildChatRequestBody(prompt: String, system: String?): String {
        val messages = buildList {
            system?.let { add(ChatMessage("system", it)) }
            add(ChatMessage("user", prompt))
        }
        return json.encodeToString(ChatCompletionRequest(chatModel, messages))
    }

    /**
     * 构建 embedding 请求 JSON。
     *
     * 保持 input 与 model 字段兼容 OpenAI 风格接口。
     */
    private fun buildEmbeddingRequestBody(text: String): String {
        return json.encodeToString(EmbeddingRequest(text, embeddingModel))
    }
}

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
)

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

@Serializable
private data class EmbeddingRequest(
    val input: String,
    val model: String,
)

@Serializable
private data class EmbeddingApiResponse(
    val data: List<EmbeddingData> = emptyList(),
    val model: String = "unknown",
)

@Serializable
private data class EmbeddingData(
    val embedding: List<Float> = emptyList(),
)
