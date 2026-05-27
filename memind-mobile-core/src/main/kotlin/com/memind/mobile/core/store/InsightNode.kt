package com.memind.mobile.core.store

import com.memind.mobile.core.model.MemoryId
import java.util.UUID

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

public enum class InsightTier {
    LEAF,
    BRANCH,
    ROOT,
}