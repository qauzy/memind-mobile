package com.memind.mobile.core.search

import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.store.MemoryItem

public interface TextSearch {
    /**
     * 执行文本关键词检索。
     *
     * memoryId 限定记忆空间，query 是查询文本，limit 控制最大返回数量。
     */
    public suspend fun search(
        memoryId: MemoryId,
        query: String,
        limit: Int = 10,
    ): List<ScoredItem>

    /**
     * 建立或更新文本索引。
     *
     * items 是本次需要加入检索索引的结构化记忆。
     */
    public suspend fun index(memoryId: MemoryId, items: List<MemoryItem>)

    /**
     * 清空指定记忆空间的文本索引。
     *
     * memoryId 用于保证不同用户/代理空间隔离。
     */
    public suspend fun clear(memoryId: MemoryId)
}

public data class ScoredItem(
    val item: MemoryItem,
    val score: Double,
)
