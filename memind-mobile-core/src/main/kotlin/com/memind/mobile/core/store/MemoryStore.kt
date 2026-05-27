package com.memind.mobile.core.store

import com.memind.mobile.core.model.MemoryId

public interface MemoryStore {
    public suspend fun saveItem(item: MemoryItem): String

    public suspend fun getItems(memoryId: MemoryId, limit: Int = 50, offset: Int = 0): List<MemoryItem>

    public suspend fun getItemById(memoryId: MemoryId, itemId: String): MemoryItem?

    public suspend fun getItemsByIds(memoryId: MemoryId, ids: List<String>): List<MemoryItem>

    public suspend fun deleteItem(memoryId: MemoryId, itemId: String): Boolean

    public suspend fun deleteItems(memoryId: MemoryId, ids: List<String>): Int

    public suspend fun count(memoryId: MemoryId): Int

    public suspend fun saveInsight(node: InsightNode): String

    public suspend fun getInsights(memoryId: MemoryId): List<InsightNode>

    public suspend fun getInsightById(memoryId: MemoryId, insightId: String): InsightNode?

    public suspend fun deleteInsights(memoryId: MemoryId, ids: List<String>): Int

    // Store methods
    public suspend fun saveRawData(memoryId: MemoryId, rawDataId: String, content: String): String

    public suspend fun getRawDataIds(memoryId: MemoryId): List<String>

    public suspend fun markRebuildRequired(memoryId: MemoryId, reason: String)
}