package com.memind.mobile.core.store

import com.memind.mobile.core.model.MemoryCategory
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.MemoryItemType
import com.memind.mobile.core.model.MemoryScope
import com.memind.mobile.core.model.RawData

public interface MemoryStore {
    /**
     * 保存或替换一条结构化记忆。
     *
     * 返回 item id，供抽取结果和索引层建立关联。
     */
    public suspend fun saveItem(item: MemoryItem): String

    /**
     * 分页读取结构化记忆。
     *
     * 支持 scope、category 和 type 过滤，贴近原版 Memind 的 RetrievalRequest 语义。
     */
    public suspend fun getItems(
        memoryId: MemoryId,
        limit: Int = 50,
        offset: Int = 0,
        scope: MemoryScope? = null,
        categories: Set<MemoryCategory>? = null,
        type: MemoryItemType? = null,
    ): List<MemoryItem>

    /**
     * 按 ID 读取单条记忆。
     *
     * 返回 null 表示该记忆不存在或不属于当前 memoryId。
     */
    public suspend fun getItemById(memoryId: MemoryId, itemId: String): MemoryItem?

    /**
     * 按 ID 集合批量读取记忆。
     *
     * 实现应尽量保持入参 ids 的顺序，便于检索层回填排序。
     */
    public suspend fun getItemsByIds(memoryId: MemoryId, ids: List<String>): List<MemoryItem>

    /**
     * 删除单条记忆。
     *
     * 返回 true 表示实际删除了记录。
     */
    public suspend fun deleteItem(memoryId: MemoryId, itemId: String): Boolean

    /**
     * 批量删除记忆。
     *
     * 返回实际删除数量，供上层同步清理索引和缓存。
     */
    public suspend fun deleteItems(memoryId: MemoryId, ids: List<String>): Int

    /**
     * 统计当前记忆空间的 item 数量。
     *
     * 用于存储预算、测试断言和管理展示。
     */
    public suspend fun count(memoryId: MemoryId): Int

    /**
     * 保存或替换 Insight 节点。
     *
     * Insight 是原版 Memind 的分层理解结构基础。
     */
    public suspend fun saveInsight(node: InsightNode): String

    /**
     * 获取当前记忆空间的 Insight 列表。
     *
     * 返回 leaf、branch、root 节点的领域模型。
     */
    public suspend fun getInsights(memoryId: MemoryId): List<InsightNode>

    /**
     * 按 ID 获取 Insight。
     *
     * 返回 null 表示不存在或不属于当前 memoryId。
     */
    public suspend fun getInsightById(memoryId: MemoryId, insightId: String): InsightNode?

    /**
     * 批量删除 Insight。
     *
     * 返回实际删除数量。
     */
    public suspend fun deleteInsights(memoryId: MemoryId, ids: List<String>): Int

    /**
     * 保存 RawData 原始输入。
     *
     * RawData 用于支撑原文回溯、caption 和三层检索。
     */
    public suspend fun saveRawData(rawData: RawData): String

    /**
     * 使用基础字段保存 RawData。
     *
     * 这是兼容旧调用方的便利函数，会包装为 RawData 模型后再保存。
     */
    public suspend fun saveRawData(memoryId: MemoryId, rawDataId: String, content: String): String =
        saveRawData(
            RawData(
                id = rawDataId,
                memoryId = memoryId,
                content = content,
            ),
        )

    /**
     * 获取当前记忆空间的 RawData ID 列表。
     *
     * 用于轻量调试、分页索引和管理展示。
     */
    public suspend fun getRawDataIds(memoryId: MemoryId): List<String>

    /**
     * 按 ID 获取 RawData。
     *
     * 用于从 MemoryItem 或检索结果回溯原始输入。
     */
    public suspend fun getRawData(memoryId: MemoryId, rawDataId: String): RawData?

    /**
     * 分页读取 RawData。
     *
     * 避免移动端一次性加载大量原始内容。
     */
    public suspend fun getRawData(memoryId: MemoryId, limit: Int = 50, offset: Int = 0): List<RawData>

    /**
     * 保存一条缓冲消息。
     *
     * kind 用于区分 pending 与 recent，支撑阶段 3 的 commit 语义和后续 getContext。
     */
    public suspend fun saveBufferMessage(message: BufferMessage): String

    /**
     * 读取缓冲消息。
     *
     * 返回按写入时间升序排列的最多 limit 条消息。
     */
    public suspend fun getBufferMessages(
        memoryId: MemoryId,
        kind: BufferKind,
        limit: Int = 50,
    ): List<BufferMessage>

    /**
     * 清空指定类型缓冲区。
     *
     * commit drain pending buffer 后会调用该函数。
     */
    public suspend fun clearBuffer(memoryId: MemoryId, kind: BufferKind): Int

    /**
     * 裁剪指定类型缓冲区。
     *
     * recent buffer 使用该函数保留最近 maxMessages 条，避免移动端无限增长。
     */
    public suspend fun trimBuffer(memoryId: MemoryId, kind: BufferKind, maxMessages: Int): Int

    /**
     * 标记当前记忆空间需要重建派生结构。
     *
     * 后续 Insight Tree 和 MemoryThread 重建会消费该标记。
     */
    public suspend fun markRebuildRequired(memoryId: MemoryId, reason: String)
}
