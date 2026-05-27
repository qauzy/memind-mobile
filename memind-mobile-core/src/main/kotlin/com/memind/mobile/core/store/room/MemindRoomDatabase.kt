package com.memind.mobile.core.store.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RoomMemoryItemEntity::class,
        RoomRawDataEntity::class,
        RoomInsightEntity::class,
        RoomBufferMessageEntity::class,
        RoomVectorMetadataEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
internal abstract class MemindRoomDatabase : RoomDatabase() {
    /**
     * 获取 MemoryItem DAO。
     *
     * 负责结构化记忆 item 的持久化读写。
     */
    abstract fun memoryItemDao(): RoomMemoryItemDao

    /**
     * 获取 RawData DAO。
     *
     * 负责原始输入和 caption 元数据的持久化读写。
     */
    abstract fun rawDataDao(): RoomRawDataDao

    /**
     * 获取 Insight DAO。
     *
     * 负责 leaf、branch、root 节点的持久化读写。
     */
    abstract fun insightDao(): RoomInsightDao

    /**
     * 获取缓冲消息 DAO。
     *
     * 阶段 3 的 pending/recent buffer 会使用该 DAO。
     */
    abstract fun bufferMessageDao(): RoomBufferMessageDao

    /**
     * 获取向量元数据 DAO。
     *
     * 阶段 5 的混合检索会使用该 DAO 管理 vectorId 关联。
     */
    abstract fun vectorMetadataDao(): RoomVectorMetadataDao
}
