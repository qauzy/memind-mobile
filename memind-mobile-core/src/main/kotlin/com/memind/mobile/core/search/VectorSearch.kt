package com.memind.mobile.core.search

import com.memind.mobile.core.model.MemoryId

public interface VectorSearch {
    public suspend fun search(
        memoryId: MemoryId,
        queryEmbedding: List<Float>,
        limit: Int = 10,
    ): List<String>

    public suspend fun index(
        memoryId: MemoryId,
        itemId: String,
        embedding: List<Float>,
    )

    public suspend fun delete(memoryId: MemoryId, ids: List<String>): Int

    public suspend fun clear(memoryId: MemoryId)
}