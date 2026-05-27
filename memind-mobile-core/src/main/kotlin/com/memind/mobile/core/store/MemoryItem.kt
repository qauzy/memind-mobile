package com.memind.mobile.core.store

import com.memind.mobile.core.model.MemoryCategory
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.MemoryItemType
import com.memind.mobile.core.model.MemoryScope
import kotlinx.serialization.Serializable

@Serializable
public data class MemoryItem(
    val id: String,
    val memoryId: MemoryId,
    val text: String,
    val scope: MemoryScope = MemoryScope.USER,
    val category: MemoryCategory? = null,
    val contentType: String = "text",
    val sourceClient: String? = null,
    val source: String? = null,
    val vectorId: String? = null,
    val rawDataId: String? = null,
    val contentHash: String? = null,
    val occurredAt: Long? = null,
    val occurredStart: Long? = null,
    val occurredEnd: Long? = null,
    val timeGranularity: String? = null,
    val observedAt: Long? = null,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = timestamp,
    val type: MemoryItemType = MemoryItemType.FACT,
) {
    /**
     * 返回用于检索和展示的主文本。
     *
     * 保留该函数是为了兼容原型代码和后续检索层统一入口。
     */
    public fun toText(): String = text

    /**
     * 解析向量 ID。
     *
     * 如果存储层没有持久化 vectorId，则使用 memoryId 与 item id 生成稳定兜底值。
     */
    public fun resolvedVectorId(): String = vectorId ?: "${memoryId.toIdentifier()}::$id"
}
