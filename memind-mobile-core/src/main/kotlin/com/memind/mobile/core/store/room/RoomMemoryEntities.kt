package com.memind.mobile.core.store.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.memind.mobile.core.model.MemoryCategory
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.MemoryItemType
import com.memind.mobile.core.model.MemoryScope
import com.memind.mobile.core.model.RawData
import com.memind.mobile.core.store.InsightNode
import com.memind.mobile.core.store.InsightTier
import com.memind.mobile.core.store.MemoryItem

@Entity(
    tableName = "memory_items",
    indices = [
        Index("memoryKey"),
        Index("scope"),
        Index("category"),
        Index("type"),
        Index("rawDataId"),
        Index("contentHash"),
    ],
)
internal data class RoomMemoryItemEntity(
    @PrimaryKey val id: String,
    val memoryKey: String,
    val userId: String,
    val agentId: String,
    val text: String,
    val scope: String,
    val category: String?,
    val contentType: String,
    val sourceClient: String?,
    val source: String?,
    val vectorId: String?,
    val rawDataId: String?,
    val contentHash: String?,
    val occurredAt: Long?,
    val occurredStart: Long?,
    val occurredEnd: Long?,
    val timeGranularity: String?,
    val observedAt: Long?,
    val metadataJson: String,
    val timestamp: Long,
    val createdAt: Long,
    val type: String,
) {
    /**
     * 将 Room item 实体转换为领域模型。
     *
     * 这里负责恢复 MemoryId、枚举和 metadata，避免上层感知数据库字段形态。
     */
    fun toModel(): MemoryItem =
        MemoryItem(
            id = id,
            memoryId = MemoryId(userId, agentId),
            text = text,
            scope = runCatching { MemoryScope.valueOf(scope) }.getOrDefault(MemoryScope.USER),
            category = MemoryCategory.fromName(category),
            contentType = contentType,
            sourceClient = sourceClient,
            source = source,
            vectorId = vectorId,
            rawDataId = rawDataId,
            contentHash = contentHash,
            occurredAt = occurredAt,
            occurredStart = occurredStart,
            occurredEnd = occurredEnd,
            timeGranularity = timeGranularity,
            observedAt = observedAt,
            metadata = RoomJson.decodeMap(metadataJson),
            timestamp = timestamp,
            createdAt = createdAt,
            type = runCatching { MemoryItemType.valueOf(type) }.getOrDefault(MemoryItemType.FACT),
        )

    companion object {
        /**
         * 将领域 MemoryItem 转换为 Room 实体。
         *
         * memoryKey 冗余保存是为了移动端查询时避免频繁拼接 userId/agentId。
         */
        fun fromModel(item: MemoryItem): RoomMemoryItemEntity =
            RoomMemoryItemEntity(
                id = item.id,
                memoryKey = item.memoryId.toIdentifier(),
                userId = item.memoryId.userId,
                agentId = item.memoryId.agentId,
                text = item.text,
                scope = item.scope.name,
                category = item.category?.categoryName,
                contentType = item.contentType,
                sourceClient = item.sourceClient,
                source = item.source,
                vectorId = item.vectorId,
                rawDataId = item.rawDataId,
                contentHash = item.contentHash,
                occurredAt = item.occurredAt,
                occurredStart = item.occurredStart,
                occurredEnd = item.occurredEnd,
                timeGranularity = item.timeGranularity,
                observedAt = item.observedAt,
                metadataJson = RoomJson.encodeMap(item.metadata),
                timestamp = item.timestamp,
                createdAt = item.createdAt,
                type = item.type.name,
            )
    }
}

@Entity(
    tableName = "raw_data",
    indices = [
        Index("memoryKey"),
        Index("contentType"),
        Index("sourceClient"),
        Index("captionVectorId"),
    ],
)
internal data class RoomRawDataEntity(
    @PrimaryKey val id: String,
    val memoryKey: String,
    val userId: String,
    val agentId: String,
    val content: String,
    val contentType: String,
    val sourceClient: String?,
    val contentId: String?,
    val caption: String,
    val captionVectorId: String?,
    val metadataJson: String,
    val resourceId: String?,
    val mimeType: String?,
    val createdAt: Long,
    val startTime: Long?,
    val endTime: Long?,
) {
    /**
     * 将 Room RawData 实体转换为领域模型。
     *
     * RawData 保留原始输入和 caption 元数据，是三层检索的第三层基础。
     */
    fun toModel(): RawData =
        RawData(
            id = id,
            memoryId = MemoryId(userId, agentId),
            content = content,
            contentType = contentType,
            sourceClient = sourceClient,
            contentId = contentId,
            caption = caption,
            captionVectorId = captionVectorId,
            metadata = RoomJson.decodeMap(metadataJson),
            resourceId = resourceId,
            mimeType = mimeType,
            createdAt = createdAt,
            startTime = startTime,
            endTime = endTime,
        )

    companion object {
        /**
         * 将领域 RawData 转换为 Room 实体。
         *
         * metadata 使用 JSON 存储，方便后续扩展 resource/mime 等字段。
         */
        fun fromModel(rawData: RawData): RoomRawDataEntity =
            RoomRawDataEntity(
                id = rawData.id,
                memoryKey = rawData.memoryId.toIdentifier(),
                userId = rawData.memoryId.userId,
                agentId = rawData.memoryId.agentId,
                content = rawData.content,
                contentType = rawData.contentType,
                sourceClient = rawData.sourceClient,
                contentId = rawData.contentId,
                caption = rawData.caption,
                captionVectorId = rawData.captionVectorId,
                metadataJson = RoomJson.encodeMap(rawData.metadata),
                resourceId = rawData.resourceId,
                mimeType = rawData.mimeType,
                createdAt = rawData.createdAt,
                startTime = rawData.startTime,
                endTime = rawData.endTime,
            )
    }
}

@Entity(
    tableName = "insights",
    indices = [
        Index("memoryKey"),
        Index("tier"),
        Index("parentId"),
    ],
)
internal data class RoomInsightEntity(
    @PrimaryKey val id: String,
    val memoryKey: String,
    val userId: String,
    val agentId: String,
    val text: String,
    val tier: String,
    val parentId: String?,
    val childrenJson: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /**
     * 将 Room Insight 实体转换为领域节点。
     *
     * childrenJson 会恢复为可变列表，兼容当前 InsightNode 结构。
     */
    fun toModel(): InsightNode =
        InsightNode(
            id = id,
            memoryId = MemoryId(userId, agentId),
            text = text,
            tier = runCatching { InsightTier.valueOf(tier) }.getOrDefault(InsightTier.LEAF),
            parentId = parentId,
            children = RoomJson.decodeStringList(childrenJson).toMutableList(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        /**
         * 将领域 InsightNode 转换为 Room 实体。
         *
         * tier 和 parentId 用于后续 leaf、branch、root 分层查询。
         */
        fun fromModel(node: InsightNode): RoomInsightEntity =
            RoomInsightEntity(
                id = node.id,
                memoryKey = node.memoryId.toIdentifier(),
                userId = node.memoryId.userId,
                agentId = node.memoryId.agentId,
                text = node.text,
                tier = node.tier.name,
                parentId = node.parentId,
                childrenJson = RoomJson.encodeStringList(node.children),
                createdAt = node.createdAt,
                updatedAt = node.updatedAt,
            )
    }
}

@Entity(
    tableName = "buffer_messages",
    indices = [
        Index("memoryKey"),
        Index("kind"),
        Index("createdAt"),
    ],
)
internal data class RoomBufferMessageEntity(
    @PrimaryKey val id: String,
    val memoryKey: String,
    val userId: String,
    val agentId: String,
    val role: String,
    val content: String,
    val timestamp: Long?,
    val metadataJson: String,
    val kind: String,
    val createdAt: Long,
)

@Entity(
    tableName = "vector_metadata",
    indices = [
        Index("memoryKey"),
        Index("targetType"),
        Index("targetId"),
    ],
)
internal data class RoomVectorMetadataEntity(
    @PrimaryKey val vectorId: String,
    val memoryKey: String,
    val userId: String,
    val agentId: String,
    val targetType: String,
    val targetId: String,
    val model: String? = null,
    val dimension: Int? = null,
    val metadataJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
)
