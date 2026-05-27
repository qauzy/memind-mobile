package com.memind.mobile.core.search

import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.store.MemoryItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ln

public class SimpleTextSearch : TextSearch {
    private val index = mutableMapOf<String, MutableMap<String, MemoryItem>>()
    private val mutex = Mutex()
    private val k1 = 1.2
    private val b = 0.75

    /**
     * 执行轻量 BM25 风格文本检索。
     *
     * query 会被拆成英文/数字词项和 CJK 单字词项，避免移动端引入额外分词库。
     */
    override suspend fun search(
        memoryId: MemoryId,
        query: String,
        limit: Int,
    ): List<ScoredItem> {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            val storeItems = index[key] ?: return emptyList()
            val queryTerms = tokenize(query)
            if (queryTerms.isEmpty()) return emptyList()
            val documents = storeItems.values.map { item -> item to tokenize(item.text) }
            if (documents.isEmpty()) return emptyList()
            val tokenizedById = documents.associate { (item, terms) -> item.id to terms }
            val averageLength = documents.map { it.second.size }.average().takeIf { it > 0.0 } ?: 1.0
            val documentCount = documents.size.toDouble()
            val documentFrequency = queryTerms.distinct().associateWith { term ->
                documents.count { (_, terms) -> term in terms }.coerceAtLeast(0)
            }

            return storeItems.values
                .mapNotNull { item ->
                    val terms = tokenizedById[item.id].orEmpty()
                    val termCounts = terms.groupingBy { it }.eachCount()
                    val score = queryTerms.distinct().sumOf { term ->
                        val frequency = termCounts[term] ?: 0
                        if (frequency == 0) {
                            0.0
                        } else {
                            val df = documentFrequency[term]?.toDouble() ?: 0.0
                            val idf = ln(1.0 + (documentCount - df + 0.5) / (df + 0.5))
                            val lengthNorm = frequency + k1 * (1.0 - b + b * terms.size / averageLength)
                            idf * (frequency * (k1 + 1.0)) / lengthNorm
                        }
                    }
                    if (score <= 0.0) return@mapNotNull null
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

    /**
     * 拆分检索词项。
     *
     * 拉丁字母和数字连续成词，CJK 字符按单字成词，兼顾英文和中文短查询。
     */
    private fun tokenize(text: String): List<String> {
        val terms = mutableListOf<String>()
        val current = StringBuilder()
        text.lowercase().forEach { char ->
            when {
                char.isLetterOrDigit() && !char.isCjk() -> current.append(char)
                char.isCjk() -> {
                    if (current.isNotEmpty()) {
                        terms.add(current.toString())
                        current.clear()
                    }
                    terms.add(char.toString())
                }
                else -> {
                    if (current.isNotEmpty()) {
                        terms.add(current.toString())
                        current.clear()
                    }
                }
            }
        }
        if (current.isNotEmpty()) {
            terms.add(current.toString())
        }
        return terms.filter { it.isNotBlank() }
    }

    /**
     * 判断字符是否属于常见 CJK 文字区间。
     *
     * 这里不用外部分词器，保持 core 模块轻量和 Android-free。
     */
    private fun Char.isCjk(): Boolean {
        val block = Character.UnicodeBlock.of(this)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
    }
}
