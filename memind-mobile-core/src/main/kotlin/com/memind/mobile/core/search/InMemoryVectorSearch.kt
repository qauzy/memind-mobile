package com.memind.mobile.core.search

import com.memind.mobile.core.model.MemoryId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

public class InMemoryVectorSearch : VectorSearch {
    private val vectors = mutableMapOf<String, MutableMap<String, List<Float>>>()
    private val mutex = Mutex()

    /**
     * 执行内存向量检索。
     *
     * queryEmbedding 是查询向量，返回余弦相似度最高的 item id。
     */
    override suspend fun search(
        memoryId: MemoryId,
        queryEmbedding: List<Float>,
        limit: Int,
    ): List<String> {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            val storeVectors = vectors[key] ?: return emptyList()

            // 内存实现直接遍历当前 memoryId 的向量，适合小规模移动端本地缓存。
            return storeVectors.entries
                .map { (id, embedding) -> id to cosineSimilarity(queryEmbedding, embedding) }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        }
    }

    /**
     * 写入单条向量索引。
     *
     * itemId 与 embedding 一一对应，按 memoryId 分桶保存。
     */
    override suspend fun index(
        memoryId: MemoryId,
        itemId: String,
        embedding: List<Float>,
    ) {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            vectors.getOrPut(key) { mutableMapOf() }[itemId] = embedding
        }
    }

    /**
     * 删除指定 item 的向量。
     *
     * 返回实际删除数量，方便调用方判断索引是否发生变化。
     */
    override suspend fun delete(memoryId: MemoryId, ids: List<String>): Int {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            val count = ids.count { vectors[key]?.remove(it) != null }
            return count
        }
    }

    /**
     * 清空指定记忆空间的所有向量。
     *
     * memoryId 用于隔离不同用户/代理空间。
     */
    override suspend fun clear(memoryId: MemoryId) {
        mutex.withLock {
            vectors.remove(memoryId.toIdentifier())
        }
    }

    /**
     * 计算两个向量的余弦相似度。
     *
     * 向量维度不一致或零向量时返回 0，避免异常打断检索链路。
     */
    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Double {
        if (a.size != b.size) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i].toDouble()
            normA += a[i].toDouble() * a[i].toDouble()
            normB += b[i].toDouble() * b[i].toDouble()
        }
        return if (normA == 0.0 || normB == 0.0) 0.0 else dot / (sqrt(normA) * sqrt(normB))
    }
}
