package com.memind.mobile.core.llm

public data class ChatResponse(
    val content: String,
    val model: String = "unknown",
    val usage: Map<String, Int> = emptyMap(),
)

public data class EmbeddingResponse(
    val embedding: List<Float>,
    val model: String = "unknown",
)

public interface ChatClient {
    public suspend fun chat(prompt: String, systemMessage: String? = null): ChatResponse

    public suspend fun embed(text: String): EmbeddingResponse

    public suspend fun health(): Boolean
}