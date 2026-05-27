package com.memind.mobile.core

import com.memind.mobile.core.insight.InsightTree
import com.memind.mobile.core.model.AddResult
import com.memind.mobile.core.model.ExtractionConfig
import com.memind.mobile.core.model.ExtractionResult
import com.memind.mobile.core.model.HealthStatus
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.Message
import com.memind.mobile.core.model.RetrievalConfig
import com.memind.mobile.core.model.RetrievalResult
import com.memind.mobile.core.model.Strategy

/**
 * Memind-Mobile core API — the primary entry point for all memory operations.
 *
 * Callers obtain a [Memory] instance through [Memory.builder]:
 * ```
 * val memory = Memory.builder()
 *     .chatClient(OpenAiClient(apiKey = "..."))
 *     .chatClient(...)
 *     .store(InMemoryStore())
 *     .build()
 *
 * val result = memory.addMessage(MemoryId("user-1", "agent-1"), Message.user("Hello"))
 * ```
 */
public interface Memory : AutoCloseable {
    // ===== Builder =====
    public companion object {
        public fun builder(): MemoryBuilder = DefaultMemoryBuilder()
    }

    // ===== Extraction =====
    public suspend fun addMessage(
        id: MemoryId,
        message: Message,
        config: ExtractionConfig = ExtractionConfig.defaults(),
    ): AddResult

    public suspend fun addMessages(
        id: MemoryId,
        messages: List<Message>,
        config: ExtractionConfig = ExtractionConfig.defaults(),
    ): AddResult

    public suspend fun extract(
        id: MemoryId,
        content: String,
        config: ExtractionConfig = ExtractionConfig.defaults(),
    ): ExtractionResult

    // ===== Commit =====
    public suspend fun commit(id: MemoryId): ExtractionResult

    public suspend fun commit(id: MemoryId, config: ExtractionConfig): ExtractionResult

    // ===== Retrieval =====
    public suspend fun retrieve(
        id: MemoryId,
        query: String,
        strategy: Strategy = Strategy.SIMPLE,
        config: RetrievalConfig = RetrievalConfig.simple(),
    ): RetrievalResult

    // ===== Insight =====
    public suspend fun getInsightTree(id: MemoryId): InsightTree

    public suspend fun flushInsights(id: MemoryId)

    // ===== Health =====
    public suspend fun health(): HealthStatus

    // ===== Lifecycle =====
    override fun close()
}