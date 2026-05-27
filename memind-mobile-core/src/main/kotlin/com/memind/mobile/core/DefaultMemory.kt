package com.memind.mobile.core

import com.memind.mobile.core.insight.InsightBuilder
import com.memind.mobile.core.insight.InsightTree
import com.memind.mobile.core.llm.ChatClient
import com.memind.mobile.core.model.AddResult
import com.memind.mobile.core.model.AddStatus
import com.memind.mobile.core.model.ExtractionConfig
import com.memind.mobile.core.model.ExtractionResult
import com.memind.mobile.core.model.ExtractionStatus
import com.memind.mobile.core.model.HealthStatus
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.Message
import com.memind.mobile.core.model.RetrievalConfig
import com.memind.mobile.core.model.RetrievalResult
import com.memind.mobile.core.model.RetrievalStatus
import com.memind.mobile.core.model.Strategy
import com.memind.mobile.core.search.SimpleTextSearch
import com.memind.mobile.core.search.TextSearch
import com.memind.mobile.core.search.VectorSearch
import com.memind.mobile.core.store.MemoryItem
import com.memind.mobile.core.store.MemoryStore
import com.memind.mobile.core.store.InMemoryStore
import java.util.concurrent.atomic.AtomicBoolean

public class DefaultMemory(
    private val chatClient: ChatClient,
    private val store: MemoryStore = InMemoryStore(),
    private val textSearch: TextSearch = SimpleTextSearch(),
    private val vectorSearch: VectorSearch? = null,
    private val insightBuilder: InsightBuilder = InsightBuilder(store),
) : Memory {
    private val closed = AtomicBoolean(false)

    override suspend fun addMessage(
        id: MemoryId,
        message: Message,
        config: ExtractionConfig,
    ): AddResult {
        if (closed.get()) return AddResult(AddStatus.FAILED, errorMessage = "closed")
        val item = MemoryItem(id = "${id.toIdentifier()}:${System.currentTimeMillis()}", memoryId = id, text = message.content)
        textSearch.index(id, listOf(item))
        store.saveItem(item)
        return AddResult(AddStatus.ACCEPTED)
    }

    override suspend fun addMessages(
        id: MemoryId,
        messages: List<Message>,
        config: ExtractionConfig,
    ): AddResult {
        messages.forEach { addMessage(id, it, config) }
        return AddResult(AddStatus.ACCEPTED)
    }

    override suspend fun extract(
        id: MemoryId,
        content: String,
        config: ExtractionConfig,
    ): ExtractionResult {
        val item = MemoryItem(id = "${id.toIdentifier()}:${System.currentTimeMillis()}", memoryId = id, text = content,
            category = "raw")
        store.saveItem(item)
        return ExtractionResult.success(id)
    }

    override suspend fun commit(id: MemoryId): ExtractionResult {
        return commit(id, ExtractionConfig.defaults())
    }

    override suspend fun commit(id: MemoryId, config: ExtractionConfig): ExtractionResult {
        return ExtractionResult.success(id)
    }

    override suspend fun retrieve(
        id: MemoryId,
        query: String,
        strategy: Strategy,
        config: RetrievalConfig,
    ): RetrievalResult {
        if (query.isBlank()) return RetrievalResult.empty("$strategy", query)

        // Simple: keyword search
        val items = store.getItems(id, limit = config.maxResults)
        val searchResults = textSearch.search(id, query, limit = config.maxResults)

        val resultItems = searchResults.map { it.item }
        val result = RetrievalResult(
            items = resultItems.map { item ->
                RetrievalResult.ScoredItem(
                    id = item.id,
                    text = item.text,
                    score = 1.0,
                )
            },
            strategy = strategy.name,
            query = query,
            status = if (resultItems.isEmpty()) RetrievalStatus.EMPTY else RetrievalStatus.SUCCESS,
        )
        return result
    }

    override suspend fun getInsightTree(id: MemoryId): InsightTree {
        return insightBuilder.buildTree(id)
    }

    override suspend fun flushInsights(id: MemoryId) {
        // insightBuilder.flush already called on demand
    }

    override suspend fun health(): HealthStatus {
        val llmOk = chatClient.health()
        return HealthStatus(
            status = if (llmOk) "UP" else "DEGRADED",
            message = if (llmOk) "All systems operational" else "LLM client unavailable",
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
    }
}