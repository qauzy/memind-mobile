package com.memind.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class ContextWindow(
    val recentMessages: List<Message> = emptyList(),
    val memories: RetrievalResult = RetrievalResult.empty("SIMPLE", ""),
    val totalTokens: Int = 0,
) {
    public val isBufferOnly: Boolean get() = memories.isEmpty

    /**
     * 格式化检索到的记忆内容。
     *
     * 该文本可直接注入宿主 App 的 LLM prompt。
     */
    public fun formattedMemories(): String = memories.formattedResult()

    public companion object {
        /**
         * 创建仅包含近期消息的上下文窗口。
         *
         * 当禁用记忆检索或没有可用记忆时使用。
         */
        public fun bufferOnly(messages: List<Message>, totalTokens: Int = 0): ContextWindow =
            ContextWindow(recentMessages = messages, totalTokens = totalTokens)
    }
}
