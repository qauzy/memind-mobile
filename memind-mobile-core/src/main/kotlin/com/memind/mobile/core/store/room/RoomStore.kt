package com.memind.mobile.core.store.room

import android.content.Context
import androidx.room.Room
import com.memind.mobile.core.model.MemoryCategory
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.MemoryItemType
import com.memind.mobile.core.model.MemoryScope
import com.memind.mobile.core.model.RawData
import com.memind.mobile.core.store.InsightNode
import com.memind.mobile.core.store.MemoryItem
import com.memind.mobile.core.store.MemoryStore

public class RoomStore internal constructor(
    private val database: MemindRoomDatabase,
) : MemoryStore {
    private val itemDao = database.memoryItemDao()
    private val rawDataDao = database.rawDataDao()
    private val insightDao = database.insightDao()

    /**
     * 保存或替换记忆 item。
     *
     * RoomStore 将领域模型转换为数据库实体，保持上层不依赖 Room 注解。
     */
    override suspend fun saveItem(item: MemoryItem): String {
        itemDao.upsert(RoomMemoryItemEntity.fromModel(item))
        return item.id
    }

    /**
     * 分页读取记忆 item。
     *
     * 阶段 2 先对过滤条件做轻量实现，后续可逐步下推到 SQL 查询。
     */
    override suspend fun getItems(
        memoryId: MemoryId,
        limit: Int,
        offset: Int,
        scope: MemoryScope?,
        categories: Set<MemoryCategory>?,
        type: MemoryItemType?,
    ): List<MemoryItem> {
        val memoryKey = memoryId.toIdentifier()
        val unfiltered = scope == null && categories.isNullOrEmpty() && type == null
        val categoryNames = categories.orEmpty().map { it.categoryName }.toSet()
        // 无过滤时直接使用 SQL 分页；有过滤时先全量取出当前 memoryKey，再在本地应用统一过滤语义。
        val entities = if (unfiltered) {
            itemDao.getItems(memoryKey, limit, offset)
        } else {
            itemDao.getAllItems(memoryKey)
                .asSequence()
                .filter { scope == null || it.scope == scope.name }
                .filter { categoryNames.isEmpty() || it.category in categoryNames }
                .filter { type == null || it.type == type.name }
                .drop(offset)
                .take(limit)
                .toList()
        }
        return entities.map { it.toModel() }
    }

    /**
     * 按 ID 获取单条记忆 item。
     *
     * 返回 null 表示不存在或不属于当前 memoryId。
     */
    override suspend fun getItemById(memoryId: MemoryId, itemId: String): MemoryItem? =
        itemDao.getItemById(memoryId.toIdentifier(), itemId)?.toModel()

    /**
     * 按指定 ID 顺序批量获取记忆 item。
     *
     * Room 的 IN 查询不保证顺序，因此这里按入参 ids 重新排序。
     */
    override suspend fun getItemsByIds(memoryId: MemoryId, ids: List<String>): List<MemoryItem> {
        if (ids.isEmpty()) return emptyList()
        val byId = itemDao.getItemsByIds(memoryId.toIdentifier(), ids)
            .associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toModel() }
    }

    /**
     * 删除单条记忆 item。
     *
     * 返回 true 表示数据库中确实删除了记录。
     */
    override suspend fun deleteItem(memoryId: MemoryId, itemId: String): Boolean =
        itemDao.deleteItem(memoryId.toIdentifier(), itemId) > 0

    /**
     * 批量删除记忆 item。
     *
     * 返回受影响行数，便于同步清理索引。
     */
    override suspend fun deleteItems(memoryId: MemoryId, ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        return itemDao.deleteItems(memoryId.toIdentifier(), ids)
    }

    /**
     * 统计当前记忆空间的 item 数量。
     *
     * 用于移动端容量预算和测试断言。
     */
    override suspend fun count(memoryId: MemoryId): Int =
        itemDao.count(memoryId.toIdentifier())

    /**
     * 保存或替换 Insight 节点。
     *
     * 当前保存轻量 tree 节点，后续会扩展为完整 insight point 模型。
     */
    override suspend fun saveInsight(node: InsightNode): String {
        insightDao.upsert(RoomInsightEntity.fromModel(node))
        return node.id
    }

    /**
     * 获取当前记忆空间的全部 Insight。
     *
     * 返回领域模型列表，供 InsightTree 构建和 UI 使用。
     */
    override suspend fun getInsights(memoryId: MemoryId): List<InsightNode> =
        insightDao.getInsights(memoryId.toIdentifier()).map { it.toModel() }

    /**
     * 按 ID 获取 Insight。
     *
     * 返回 null 表示不存在或不属于当前 memoryId。
     */
    override suspend fun getInsightById(memoryId: MemoryId, insightId: String): InsightNode? =
        insightDao.getInsightById(memoryId.toIdentifier(), insightId)?.toModel()

    /**
     * 批量删除 Insight。
     *
     * 返回受影响行数，供上层刷新检索缓存。
     */
    override suspend fun deleteInsights(memoryId: MemoryId, ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        return insightDao.deleteInsights(memoryId.toIdentifier(), ids)
    }

    /**
     * 保存或替换 RawData。
     *
     * RawData 记录原始输入，是原文回溯和 raw-data tier 的基础。
     */
    override suspend fun saveRawData(rawData: RawData): String {
        rawDataDao.upsert(RoomRawDataEntity.fromModel(rawData))
        return rawData.id
    }

    /**
     * 获取当前记忆空间的 RawData ID 列表。
     *
     * 用于调试、分页索引和轻量管理界面。
     */
    override suspend fun getRawDataIds(memoryId: MemoryId): List<String> =
        rawDataDao.getRawDataIds(memoryId.toIdentifier())

    /**
     * 按 ID 获取 RawData。
     *
     * 用于从检索到的 item 回溯原始片段。
     */
    override suspend fun getRawData(memoryId: MemoryId, rawDataId: String): RawData? =
        rawDataDao.getRawData(memoryId.toIdentifier(), rawDataId)?.toModel()

    /**
     * 分页读取 RawData。
     *
     * 避免移动端一次性加载过多原始内容。
     */
    override suspend fun getRawData(memoryId: MemoryId, limit: Int, offset: Int): List<RawData> =
        rawDataDao.getRawData(memoryId.toIdentifier(), limit, offset).map { it.toModel() }

    /**
     * 标记派生结构需要重建。
     *
     * 阶段 2 暂不落库 dirty flag，后续 Insight/Thread 阶段会实现。
     */
    override suspend fun markRebuildRequired(memoryId: MemoryId, reason: String) {
        // 阶段 2 先保持空实现，后续会持久化 insight/thread 的 dirty flag。
    }

    public companion object {
        /**
         * 创建 RoomStore 实例。
         *
         * 使用宿主 App 的 Context 创建 Room 数据库，数据库文件默认位于应用私有目录。
         */
        public fun create(
            context: Context,
            databaseName: String = "memind-mobile.db",
        ): RoomStore =
            RoomStore(
                Room.databaseBuilder(
                    context.applicationContext,
                    MemindRoomDatabase::class.java,
                    databaseName,
                ).build(),
            )
    }
}
