package com.memind.mobile.core.search

import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.store.MemoryItem

public interface TextSearch {
    public suspend fun search(
        memoryId: MemoryId,
        query: String,
        limit: Int = 10,
    ): List<ScoredItem>

    public suspend fun index(memoryId: MemoryId, items: List<MemoryItem>)

    public suspend fun clear(memoryId: MemoryId)
}

public data class ScoredItem(
    val item: MemoryItem,
    val score: Double,
)