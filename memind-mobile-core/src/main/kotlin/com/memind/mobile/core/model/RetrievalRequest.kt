package com.memind.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class RetrievalRequest(
    val memoryId: MemoryId,
    val query: String,
    val conversationHistory: List<String> = emptyList(),
    val config: RetrievalConfig = RetrievalConfig.simple(),
    val metadata: Map<String, String> = emptyMap(),
    val scope: MemoryScope? = null,
    val categories: Set<MemoryCategory>? = null,
) {
    public companion object {
        /**
         * 创建通用检索请求。
         *
         * 根据 strategy 自动选择 simple 或 deep 配置。
         */
        public fun of(
            memoryId: MemoryId,
            query: String,
            strategy: Strategy = Strategy.SIMPLE,
        ): RetrievalRequest =
            RetrievalRequest(
                memoryId = memoryId,
                query = query,
                config = if (strategy == Strategy.DEEP) RetrievalConfig.deep() else RetrievalConfig.simple(),
            )

        /**
         * 创建只检索用户记忆的请求。
         *
         * 用于只需要 profile、behavior、event 的场景。
         */
        public fun userMemory(
            memoryId: MemoryId,
            query: String,
            strategy: Strategy = Strategy.SIMPLE,
        ): RetrievalRequest = of(memoryId, query, strategy).copy(scope = MemoryScope.USER)

        /**
         * 创建只检索代理记忆的请求。
         *
         * 用于只需要 tool、directive、playbook、resolution 的场景。
         */
        public fun agentMemory(
            memoryId: MemoryId,
            query: String,
            strategy: Strategy = Strategy.SIMPLE,
        ): RetrievalRequest = of(memoryId, query, strategy).copy(scope = MemoryScope.AGENT)

        /**
         * 创建按分类过滤的检索请求。
         *
         * categories 使用原版 Memind 的七类记忆分类。
         */
        public fun byCategories(
            memoryId: MemoryId,
            query: String,
            categories: Set<MemoryCategory>,
            strategy: Strategy = Strategy.SIMPLE,
        ): RetrievalRequest = of(memoryId, query, strategy).copy(categories = categories)
    }
}
