package com.memind.mobile.core.store

import com.memind.mobile.core.model.MemoryId
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
public data class InsightNode(
    val id: String = UUID.randomUUID().toString(),
    val memoryId: MemoryId,
    val text: String,
    val tier: InsightTier = InsightTier.LEAF,
    val parentId: String? = null,
    val children: MutableList<String> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
public enum class InsightTier {
    LEAF,
    BRANCH,
    ROOT,
}
