package com.memind.mobile.core.search

import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.store.MemoryItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class SimpleTextSearch : TextSearch {
    private val index = mutableMapOf<String, MutableMap<String, MemoryItem>>()
    private val mutex = Mutex()

    override suspend fun search(
        memoryId: MemoryId,
        query: String,
        limit: Int,
    ): List<ScoredItem> {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            val storeItems = index[key] ?: return emptyList()
            val queryWords = query.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

            return storeItems.values
                .map { item ->
                    val textLower = item.text.lowercase()
                    val wordCount = queryWords.count { word -> textLower.contains(word) }
                    val score = if (queryWords.isEmpty()) 0.0
                    else wordCount.toDouble() / queryWords.size.toDouble()
                    ScoredItem(item, score)
                }
                .sortedByDescending { it.score }
                .take(limit)
        }
    }

    override suspend fun index(memoryId: MemoryId, items: List<MemoryItem>) {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            val map = index.getOrPut(key) { mutableMapOf() }
            items.forEach { map[it.id] = it }
        }
    }

    override suspend fun clear(memoryId: MemoryId) {
        mutex.withLock {
            index.remove(memoryId.toIdentifier())
        }
    }
}