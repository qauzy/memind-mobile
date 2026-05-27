package com.memind.mobile.core.store

import com.memind.mobile.core.model.MemoryId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class InMemoryStore : MemoryStore {
    private val items = mutableMapOf<String, MutableList<MemoryItem>>()
    private val insights = mutableMapOf<String, MutableList<InsightNode>>()
    private val rawData = mutableMapOf<String, MutableList<String>>()
    private val rebuildFlags = mutableMapOf<String, String>()
    private val mutex = Mutex()

    override suspend fun saveItem(item: MemoryItem): String {
        mutex.withLock {
            val key = item.memoryId.toIdentifier()
            items.getOrPut(key) { mutableListOf() }.add(item)
        }
        return item.id
    }

    override suspend fun getItems(memoryId: MemoryId, limit: Int, offset: Int): List<MemoryItem> {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            return items[key]?.drop(offset)?.take(limit) ?: emptyList()
        }
    }

    override suspend fun getItemById(memoryId: MemoryId, itemId: String): MemoryItem? {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            return items[key]?.find { it.id == itemId }
        }
    }

    override suspend fun getItemsByIds(memoryId: MemoryId, ids: List<String>): List<MemoryItem> {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            return items[key]?.filter { it.id in ids } ?: emptyList()
        }
    }

    override suspend fun deleteItem(memoryId: MemoryId, itemId: String): Boolean {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            return items[key]?.removeAll { it.id == itemId } ?: false
        }
    }

    override suspend fun deleteItems(memoryId: MemoryId, ids: List<String>): Int {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            return items[key]?.removeAll { it.id in ids }?.let { 1 } ?: 0
        }
    }

    override suspend fun count(memoryId: MemoryId): Int {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            return items[key]?.size ?: 0
        }
    }

    override suspend fun saveInsight(node: InsightNode): String {
        mutex.withLock {
            val key = node.memoryId.toIdentifier()
            insights.getOrPut(key) { mutableListOf() }.add(node)
        }
        return node.id
    }

    override suspend fun getInsights(memoryId: MemoryId): List<InsightNode> {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            return insights[key]?.toList() ?: emptyList()
        }
    }

    override suspend fun getInsightById(memoryId: MemoryId, insightId: String): InsightNode? {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            return insights[key]?.find { it.id == insightId }
        }
    }

    override suspend fun deleteInsights(memoryId: MemoryId, ids: List<String>): Int {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            return insights[key]?.removeAll { it.id in ids }?.let { 1 } ?: 0
        }
    }

    override suspend fun saveRawData(memoryId: MemoryId, rawDataId: String, content: String): String {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            rawData.getOrPut(key) { mutableListOf() }.add(rawDataId)
        }
        return rawDataId
    }

    override suspend fun getRawDataIds(memoryId: MemoryId): List<String> {
        mutex.withLock {
            return rawData[memoryId.toIdentifier()]?.toList() ?: emptyList()
        }
    }

    override suspend fun markRebuildRequired(memoryId: MemoryId, reason: String) {
        mutex.withLock {
            rebuildFlags[memoryId.toIdentifier()] = reason
        }
    }
}