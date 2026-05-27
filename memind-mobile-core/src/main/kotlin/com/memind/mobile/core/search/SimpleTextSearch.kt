package com.memind.mobile.core.search

import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.store.MemoryItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class SimpleTextSearch : TextSearch {
    private val index = mutableMapOf<String, MutableMap<String, MemoryItem>>()
    private val mutex = Mutex()

    /**
     * 执行轻量关键词检索。
     *
     * query 会被拆成词项并按命中比例打分，返回按分数降序排列的结果。
     */
    override suspend fun search(
        memoryId: MemoryId,
        query: String,
        limit: Int,
    ): List<ScoredItem> {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            val storeItems = index[key] ?: return emptyList()
            // 移动端默认采用低成本分词方式，避免阶段 2 引入额外分词库和初始化开销。
            val queryWords = query.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

            return storeItems.values
                .map { item ->
                    val textLower = item.text.lowercase()
                    val wordCount = queryWords.count { word -> textLower.contains(word) }
                    // 分数使用命中词比例，便于与后续向量分数做轻量融合。
                    val score = if (queryWords.isEmpty()) 0.0
                    else wordCount.toDouble() / queryWords.size.toDouble()
                    ScoredItem(item, score)
                }
                .sortedByDescending { it.score }
                .take(limit)
        }
    }

    /**
     * 写入文本检索索引。
     *
     * items 会按 memoryId 分桶保存，避免不同用户/代理空间互相污染。
     */
    override suspend fun index(memoryId: MemoryId, items: List<MemoryItem>) {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            val map = index.getOrPut(key) { mutableMapOf() }
            items.forEach { map[it.id] = it }
        }
    }

    /**
     * 清空指定记忆空间的文本索引。
     *
     * memoryId 决定被清理的索引分桶。
     */
    override suspend fun clear(memoryId: MemoryId) {
        mutex.withLock {
            index.remove(memoryId.toIdentifier())
        }
    }
}
