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
    /**
     * 调用聊天模型生成文本响应。
     *
     * prompt 是用户输入，systemMessage 是可选系统提示，返回模型回复内容。
     */
    public suspend fun chat(prompt: String, systemMessage: String? = null): ChatResponse

    /**
     * 调用 embedding 模型生成文本向量。
     *
     * text 是待向量化内容，返回向量和模型信息。
     */
    public suspend fun embed(text: String): EmbeddingResponse

    /**
     * 检查模型客户端是否可用。
     *
     * 返回 true 表示当前客户端可用于远程增强能力。
     */
    public suspend fun health(): Boolean
}
