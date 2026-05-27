package com.memind.mobile.core.search

import com.memind.mobile.core.model.MemoryId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

public class InMemoryVectorSearch : VectorSearch {
    private val vectors = mutableMapOf<String, MutableMap<String, List<Float>>>()
    private val mutex = Mutex()

    override suspend fun search(
        memoryId: MemoryId,
        queryEmbedding: List<Float>,
        limit: Int,
    ): List<String> {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            val storeVectors = vectors[key] ?: return emptyList()

            return storeVectors.entries
                .map { (id, embedding) -> id to cosineSimilarity(queryEmbedding, embedding) }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        }
    }

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

    override suspend fun delete(memoryId: MemoryId, ids: List<String>): Int {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            val count = ids.count { vectors[key]?.remove(it) != null }
            return count
        }
    }

    override suspend fun clear(memoryId: MemoryId) {
        mutex.withLock {
            vectors.remove(memoryId.toIdentifier())
        }
    }

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