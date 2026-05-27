package com.memind.mobile.core.search

import com.memind.mobile.core.model.MemoryId

public interface VectorSearch {
    /**
     * 执行向量相似度检索。
     *
     * queryEmbedding 是查询向量，返回最相关的 item id 列表。
     */
    public suspend fun search(
        memoryId: MemoryId,
        queryEmbedding: List<Float>,
        limit: Int = 10,
    ): List<String>

    /**
     * 写入单条记忆的向量索引。
     *
     * itemId 关联 MemoryItem，embedding 是对应文本向量。
     */
    public suspend fun index(
        memoryId: MemoryId,
        itemId: String,
        embedding: List<Float>,
    )

    /**
     * 删除指定 item 的向量索引。
     *
     * 返回实际删除数量，便于上层同步存储状态。
     */
    public suspend fun delete(memoryId: MemoryId, ids: List<String>): Int

    /**
     * 清空指定记忆空间的向量索引。
     *
     * 用于重建索引或删除整个记忆空间时释放索引数据。
     */
    public suspend fun clear(memoryId: MemoryId)
}
