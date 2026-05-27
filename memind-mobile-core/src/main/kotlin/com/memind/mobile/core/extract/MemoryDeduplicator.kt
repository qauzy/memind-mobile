package com.memind.mobile.core.extract

import com.memind.mobile.core.llm.ChatClient
import com.memind.mobile.core.model.ExtractionConfig
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.search.VectorSearch
import com.memind.mobile.core.store.MemoryItem
import com.memind.mobile.core.store.MemoryStore

public class MemoryDeduplicator(
    private val store: MemoryStore,
    private val vectorSearch: VectorSearch? = null,
    private val chatClient: ChatClient? = null,
) {
    /**
     * 查找重复记忆。
     *
     * 先执行精确 hash 去重；当启用语义去重且 embedding/vector 可用时，再执行近似语义查重。
     */
    public suspend fun findDuplicate(
        memoryId: MemoryId,
        config: ExtractionConfig,
        contentHash: String,
        text: String,
    ): MemoryItem? {
        val category = ExtractionSupport.defaultCategory(config.scope)
        val existing = store.getItems(
            memoryId = memoryId,
            limit = 500,
            scope = config.scope,
            categories = setOf(category),
        )
        existing.firstOrNull { it.contentHash == contentHash }?.let { return it }

        val vectors = vectorSearch ?: return null
        val client = chatClient ?: return null
        if (!config.enableSemanticDedup) return null
        val embedding = runCatching { client.embed(text).embedding }.getOrDefault(emptyList())
        if (embedding.isEmpty()) return null
        val nearestIds = vectors.search(memoryId, embedding, limit = 1)
        return store.getItemsByIds(memoryId, nearestIds).firstOrNull()
    }

    /**
     * 为新记忆建立语义去重索引。
     *
     * embedding 失败时静默降级，保证离线和弱网环境不阻塞基础抽取。
     */
    public suspend fun indexIfEnabled(
        memoryId: MemoryId,
        itemId: String,
        text: String,
        config: ExtractionConfig,
    ) {
        val vectors = vectorSearch ?: return
        val client = chatClient ?: return
        if (!config.enableSemanticDedup) return
        val embedding = runCatching { client.embed(text).embedding }.getOrDefault(emptyList())
        if (embedding.isNotEmpty()) {
            vectors.index(memoryId, itemId, embedding)
        }
    }
}
