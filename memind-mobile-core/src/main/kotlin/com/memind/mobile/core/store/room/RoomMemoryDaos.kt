package com.memind.mobile.core.store.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface RoomMemoryItemDao {
    /**
     * 插入或替换记忆 item。
     *
     * 使用 REPLACE 保证同 ID 重放写入时幂等。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoomMemoryItemEntity)

    /**
     * 分页读取当前 memoryKey 下的 item。
     *
     * 默认按创建时间升序，保持写入顺序稳定。
     */
    @Query("SELECT * FROM memory_items WHERE memoryKey = :memoryKey ORDER BY createdAt ASC LIMIT :limit OFFSET :offset")
    suspend fun getItems(memoryKey: String, limit: Int, offset: Int): List<RoomMemoryItemEntity>

    /**
     * 读取当前 memoryKey 下全部 item。
     *
     * 仅用于阶段 2 的内存级过滤，后续可下推到 SQL 条件。
     */
    @Query("SELECT * FROM memory_items WHERE memoryKey = :memoryKey ORDER BY createdAt ASC")
    suspend fun getAllItems(memoryKey: String): List<RoomMemoryItemEntity>

    /**
     * 按 ID 读取单个 item。
     *
     * memoryKey 条件用于保证不同用户/代理空间隔离。
     */
    @Query("SELECT * FROM memory_items WHERE memoryKey = :memoryKey AND id = :itemId LIMIT 1")
    suspend fun getItemById(memoryKey: String, itemId: String): RoomMemoryItemEntity?

    /**
     * 按 ID 集合批量读取 item。
     *
     * 用于向量召回或融合排序后回填完整内容。
     */
    @Query("SELECT * FROM memory_items WHERE memoryKey = :memoryKey AND id IN (:ids)")
    suspend fun getItemsByIds(memoryKey: String, ids: List<String>): List<RoomMemoryItemEntity>

    /**
     * 删除单个 item。
     *
     * 返回受影响行数，便于上层判断是否真的删除。
     */
    @Query("DELETE FROM memory_items WHERE memoryKey = :memoryKey AND id = :itemId")
    suspend fun deleteItem(memoryKey: String, itemId: String): Int

    /**
     * 批量删除 item。
     *
     * 返回受影响行数，后续用于同步清理向量和缓存。
     */
    @Query("DELETE FROM memory_items WHERE memoryKey = :memoryKey AND id IN (:ids)")
    suspend fun deleteItems(memoryKey: String, ids: List<String>): Int

    /**
     * 统计当前 memoryKey 下的 item 数量。
     *
     * 用于移动端容量预算和测试断言。
     */
    @Query("SELECT COUNT(*) FROM memory_items WHERE memoryKey = :memoryKey")
    suspend fun count(memoryKey: String): Int
}

@Dao
internal interface RoomRawDataDao {
    /**
     * 插入或替换 RawData。
     *
     * RawData 保存原始输入，便于后续 caption 检索和原文回溯。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoomRawDataEntity)

    /**
     * 读取 RawData ID 列表。
     *
     * 用于轻量索引和调试展示。
     */
    @Query("SELECT id FROM raw_data WHERE memoryKey = :memoryKey ORDER BY createdAt ASC")
    suspend fun getRawDataIds(memoryKey: String): List<String>

    /**
     * 按 ID 读取 RawData。
     *
     * memoryKey 条件保证不同记忆空间不会串读。
     */
    @Query("SELECT * FROM raw_data WHERE memoryKey = :memoryKey AND id = :rawDataId LIMIT 1")
    suspend fun getRawData(memoryKey: String, rawDataId: String): RoomRawDataEntity?

    /**
     * 分页读取 RawData。
     *
     * 后续 raw-data tier 检索会基于该分页能力做容量控制。
     */
    @Query("SELECT * FROM raw_data WHERE memoryKey = :memoryKey ORDER BY createdAt ASC LIMIT :limit OFFSET :offset")
    suspend fun getRawData(memoryKey: String, limit: Int, offset: Int): List<RoomRawDataEntity>
}

@Dao
internal interface RoomInsightDao {
    /**
     * 插入或替换 Insight。
     *
     * 用于保存 leaf、branch、root 分层节点。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoomInsightEntity)

    /**
     * 读取当前 memoryKey 的全部 Insight。
     *
     * 阶段 6 会进一步加入 dirty flag 和增量刷新。
     */
    @Query("SELECT * FROM insights WHERE memoryKey = :memoryKey ORDER BY createdAt ASC")
    suspend fun getInsights(memoryKey: String): List<RoomInsightEntity>

    /**
     * 按 ID 读取单个 Insight。
     *
     * 用于树节点定位和局部删除。
     */
    @Query("SELECT * FROM insights WHERE memoryKey = :memoryKey AND id = :insightId LIMIT 1")
    suspend fun getInsightById(memoryKey: String, insightId: String): RoomInsightEntity?

    /**
     * 批量删除 Insight。
     *
     * 返回受影响行数，便于上层刷新缓存。
     */
    @Query("DELETE FROM insights WHERE memoryKey = :memoryKey AND id IN (:ids)")
    suspend fun deleteInsights(memoryKey: String, ids: List<String>): Int
}

@Dao
internal interface RoomBufferMessageDao {
    /**
     * 插入或替换缓冲消息。
     *
     * 阶段 3 的 pending/recent buffer 会复用该表。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoomBufferMessageEntity)

    /**
     * 读取指定类型的缓冲消息。
     *
     * kind 用于区分 pending 与 recent 等缓冲类型。
     */
    @Query("SELECT * FROM buffer_messages WHERE memoryKey = :memoryKey AND kind = :kind ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getMessages(memoryKey: String, kind: String, limit: Int): List<RoomBufferMessageEntity>

    /**
     * 清空指定类型的缓冲消息。
     *
     * commit drain pending buffer 后会使用该能力。
     */
    @Query("DELETE FROM buffer_messages WHERE memoryKey = :memoryKey AND kind = :kind")
    suspend fun clear(memoryKey: String, kind: String): Int
}

@Dao
internal interface RoomVectorMetadataDao {
    /**
     * 插入或替换向量元数据。
     *
     * 仅保存 vectorId 与目标对象关系，不直接保存大向量本体。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoomVectorMetadataEntity)

    /**
     * 按目标对象读取向量元数据。
     *
     * 用于判断 item/rawData/insight 是否已经生成过 embedding。
     */
    @Query("SELECT * FROM vector_metadata WHERE memoryKey = :memoryKey AND targetType = :targetType AND targetId = :targetId LIMIT 1")
    suspend fun getByTarget(memoryKey: String, targetType: String, targetId: String): RoomVectorMetadataEntity?

    /**
     * 按 vectorId 批量删除向量元数据。
     *
     * item 删除或索引失效时同步清理。
     */
    @Query("DELETE FROM vector_metadata WHERE memoryKey = :memoryKey AND vectorId IN (:vectorIds)")
    suspend fun deleteByVectorIds(memoryKey: String, vectorIds: List<String>): Int
}
