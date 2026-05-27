package com.memind.mobile.core.store

import com.memind.mobile.core.model.MemoryCategory
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.MemoryItemType
import com.memind.mobile.core.model.MemoryScope
import com.memind.mobile.core.model.RawData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class InMemoryStore : MemoryStore {
    private val items = mutableMapOf<String, LinkedHashMap<String, MemoryItem>>()
    private val insights = mutableMapOf<String, MutableList<InsightNode>>()
    private val rawData = mutableMapOf<String, LinkedHashMap<String, RawData>>()
    private val bufferMessages = mutableMapOf<String, MutableList<BufferMessage>>()
    private val rebuildFlags = mutableMapOf<String, String>()
    private val mutex = Mutex()

    /**
     * 保存或替换记忆条目。
     *
     * 使用 LinkedHashMap 保留写入顺序，便于移动端分页读取时稳定排序。
     */
    override suspend fun saveItem(item: MemoryItem): String {
        mutex.withLock {
            val key = item.memoryId.toIdentifier()
            items.getOrPut(key) { linkedMapOf() }[item.id] = item
        }
        return item.id
    }

    /**
     * 分页读取记忆条目。
     *
     * 支持 scope、category 和 type 过滤，用于贴近原版 RetrievalRequest 的检索语义。
     */
    override suspend fun getItems(
        memoryId: MemoryId,
        limit: Int,
        offset: Int,
        scope: MemoryScope?,
        categories: Set<MemoryCategory>?,
        type: MemoryItemType?,
    ): List<MemoryItem> {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            // 内存实现以简单 sequence 过滤为主，避免在阶段 2 引入复杂索引结构。
            return items[key]
                ?.values
                ?.asSequence()
                ?.filter { scope == null || it.scope == scope }
                ?.filter { categories.isNullOrEmpty() || it.category in categories }
                ?.filter { type == null || it.type == type }
                ?.drop(offset)
                ?.take(limit)
                ?.toList()
                ?: emptyList()
        }
    }

    /**
     * 按 ID 获取单条记忆。
     *
     * 返回 null 表示当前 memoryId 下没有对应 item。
     */
    override suspend fun getItemById(memoryId: MemoryId, itemId: String): MemoryItem? {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            return items[key]?.get(itemId)
        }
    }

    /**
     * 按给定 ID 顺序批量读取记忆。
     *
     * 结果会尽量保持入参 ids 的顺序，便于后续向量召回后按融合排序回填。
     */
    override suspend fun getItemsByIds(memoryId: MemoryId, ids: List<String>): List<MemoryItem> {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            val storeItems = items[key] ?: return emptyList()
            return ids.mapNotNull { storeItems[it] }
        }
    }

    /**
     * 删除单条记忆。
     *
     * 返回 true 表示确实删除了现有记录。
     */
    override suspend fun deleteItem(memoryId: MemoryId, itemId: String): Boolean {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            return items[key]?.remove(itemId) != null
        }
    }

    /**
     * 批量删除记忆。
     *
     * 返回实际删除数量，供上层同步清理向量索引或缓存。
     */
    override suspend fun deleteItems(memoryId: MemoryId, ids: List<String>): Int {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            val storeItems = items[key] ?: return 0
            return ids.count { storeItems.remove(it) != null }
        }
    }

    /**
     * 统计当前记忆空间中的 item 数量。
     *
     * 该计数用于测试和移动端存储预算判断。
     */
    override suspend fun count(memoryId: MemoryId): Int {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            return items[key]?.size ?: 0
        }
    }

    /**
     * 保存 Insight 节点。
     *
     * 当前为轻量树结构存储，后续会扩展 points/version/dirty flag。
     */
    override suspend fun saveInsight(node: InsightNode): String {
        mutex.withLock {
            val key = node.memoryId.toIdentifier()
            insights.getOrPut(key) { mutableListOf() }.add(node)
        }
        return node.id
    }

    /**
     * 获取当前记忆空间的全部 Insight。
     *
     * 返回拷贝列表，避免调用方直接修改内部集合。
     */
    override suspend fun getInsights(memoryId: MemoryId): List<InsightNode> {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            return insights[key]?.toList() ?: emptyList()
        }
    }

    /**
     * 按 ID 获取单个 Insight。
     *
     * 用于后续 Insight Tree 局部刷新和 UI 定位。
     */
    override suspend fun getInsightById(memoryId: MemoryId, insightId: String): InsightNode? {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            return insights[key]?.find { it.id == insightId }
        }
    }

    /**
     * 批量删除 Insight。
     *
     * 返回实际删除数量，避免旧实现只返回 0/1 导致上层误判。
     */
    override suspend fun deleteInsights(memoryId: MemoryId, ids: List<String>): Int {
        mutex.withLock {
            val key = memoryId.toIdentifier()
            val storeInsights = insights[key] ?: return 0
            val before = storeInsights.size
            storeInsights.removeAll { it.id in ids }
            return before - storeInsights.size
        }
    }

    /**
     * 保存 RawData 原始数据。
     *
     * RawData 与 MemoryItem 分离，便于后续实现三层检索和原文回溯。
     */
    override suspend fun saveRawData(rawData: RawData): String {
        mutex.withLock {
            val key = rawData.memoryId.toIdentifier()
            this.rawData.getOrPut(key) { linkedMapOf() }[rawData.id] = rawData
        }
        return rawData.id
    }

    /**
     * 获取当前记忆空间的 RawData ID 列表。
     *
     * ID 顺序与写入顺序一致，方便分页或调试。
     */
    override suspend fun getRawDataIds(memoryId: MemoryId): List<String> {
        mutex.withLock {
            return rawData[memoryId.toIdentifier()]?.keys?.toList() ?: emptyList()
        }
    }

    /**
     * 按 ID 获取 RawData。
     *
     * 返回 null 表示原始数据不存在或 memoryId 不匹配。
     */
    override suspend fun getRawData(memoryId: MemoryId, rawDataId: String): RawData? {
        mutex.withLock {
            return rawData[memoryId.toIdentifier()]?.get(rawDataId)
        }
    }

    /**
     * 分页读取 RawData。
     *
     * 供后续 raw caption 检索和管理界面使用。
     */
    override suspend fun getRawData(memoryId: MemoryId, limit: Int, offset: Int): List<RawData> {
        mutex.withLock {
            return rawData[memoryId.toIdentifier()]
                ?.values
                ?.drop(offset)
                ?.take(limit)
                ?: emptyList()
        }
    }

    /**
     * 保存缓冲消息。
     *
     * 使用 memoryId 和 kind 组合成内部 key，保证 pending/recent 互不影响。
     */
    override suspend fun saveBufferMessage(message: BufferMessage): String {
        mutex.withLock {
            bufferMessages.getOrPut(bufferKey(message.memoryId, message.kind)) { mutableListOf() }
                .add(message)
        }
        return message.id
    }

    /**
     * 读取缓冲消息。
     *
     * 内存实现按 createdAt 升序返回，和 RoomStore 行为保持一致。
     */
    override suspend fun getBufferMessages(
        memoryId: MemoryId,
        kind: BufferKind,
        limit: Int,
    ): List<BufferMessage> {
        mutex.withLock {
            return bufferMessages[bufferKey(memoryId, kind)]
                ?.sortedBy { it.createdAt }
                ?.take(limit)
                ?: emptyList()
        }
    }

    /**
     * 清空缓冲消息。
     *
     * 返回清理数量，方便 commit 路径做调试和测试断言。
     */
    override suspend fun clearBuffer(memoryId: MemoryId, kind: BufferKind): Int {
        mutex.withLock {
            return bufferMessages.remove(bufferKey(memoryId, kind))?.size ?: 0
        }
    }

    /**
     * 裁剪缓冲消息。
     *
     * 仅保留最近 maxMessages 条，超过部分从列表头部移除。
     */
    override suspend fun trimBuffer(memoryId: MemoryId, kind: BufferKind, maxMessages: Int): Int {
        mutex.withLock {
            if (maxMessages < 0) return 0
            val key = bufferKey(memoryId, kind)
            val entries = bufferMessages[key] ?: return 0
            val sorted = entries.sortedBy { it.createdAt }
            val retained = sorted.takeLast(maxMessages)
            val removed = entries.size - retained.size
            bufferMessages[key] = retained.toMutableList()
            return removed
        }
    }

    /**
     * 标记当前记忆空间需要重建派生结构。
     *
     * 阶段 2 仅记录原因；后续 Insight/Thread 阶段会消费该标记。
     */
    override suspend fun markRebuildRequired(memoryId: MemoryId, reason: String) {
        mutex.withLock {
            rebuildFlags[memoryId.toIdentifier()] = reason
        }
    }

    /**
     * 生成缓冲区内部 key。
     *
     * 将 kind 纳入 key 可以复用同一张内存表保存 pending 和 recent。
     */
    private fun bufferKey(memoryId: MemoryId, kind: BufferKind): String =
        "${memoryId.toIdentifier()}:${kind.name}"
}
