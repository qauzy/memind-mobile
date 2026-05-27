package com.memind.mobile.store.json

import com.memind.mobile.core.model.MemoryCategory
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.MemoryItemType
import com.memind.mobile.core.model.MemoryScope
import com.memind.mobile.core.model.RawData
import com.memind.mobile.core.store.BufferKind
import com.memind.mobile.core.store.BufferMessage
import com.memind.mobile.core.store.InsightNode
import com.memind.mobile.core.store.MemoryItem
import com.memind.mobile.core.store.MemoryStore
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

public class JsonFileStore(
    private val rootDir: Path,
) : MemoryStore {
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val itemsFile = rootDir.resolve("items.jsonl")
    private val rawDataFile = rootDir.resolve("raw_data.jsonl")
    private val insightsFile = rootDir.resolve("insights.jsonl")
    private val buffersFile = rootDir.resolve("buffers.jsonl")
    private val rebuildFile = rootDir.resolve("rebuild_flags.jsonl")

    init {
        Files.createDirectories(rootDir)
    }

    /**
     * 保存或替换结构化记忆。
     *
     * JSONL 实现通过重写小文件保持 upsert 语义，适合通用 JVM 和轻量本地持久化。
     */
    override suspend fun saveItem(item: MemoryItem): String {
        mutex.withLock {
            val records = readLines<MemoryItem>(itemsFile)
                .filterNot { it.memoryId == item.memoryId && it.id == item.id }
                .toMutableList()
            records.add(item)
            writeLines(itemsFile, records)
        }
        return item.id
    }

    /**
     * 分页读取结构化记忆。
     *
     * 在 JSONL MVP 中先以内存过滤实现，后续大数据量场景可换 SQLite/Room 模块。
     */
    override suspend fun getItems(
        memoryId: MemoryId,
        limit: Int,
        offset: Int,
        scope: MemoryScope?,
        categories: Set<MemoryCategory>?,
        type: MemoryItemType?,
    ): List<MemoryItem> =
        mutex.withLock {
            readLines<MemoryItem>(itemsFile)
                .asSequence()
                .filter { it.memoryId == memoryId }
                .filter { scope == null || it.scope == scope }
                .filter { categories.isNullOrEmpty() || it.category in categories }
                .filter { type == null || it.type == type }
                .sortedBy { it.createdAt }
                .drop(offset)
                .take(limit)
                .toList()
        }

    /**
     * 按 ID 读取单条记忆。
     *
     * 返回 null 表示文件中不存在该记忆。
     */
    override suspend fun getItemById(memoryId: MemoryId, itemId: String): MemoryItem? =
        mutex.withLock {
            readLines<MemoryItem>(itemsFile)
                .firstOrNull { it.memoryId == memoryId && it.id == itemId }
        }

    /**
     * 按 ID 顺序批量读取记忆。
     *
     * 读取后按入参 ids 顺序回填，便于向量召回后的排序保持稳定。
     */
    override suspend fun getItemsByIds(memoryId: MemoryId, ids: List<String>): List<MemoryItem> =
        mutex.withLock {
            val byId = readLines<MemoryItem>(itemsFile)
                .filter { it.memoryId == memoryId && it.id in ids }
                .associateBy { it.id }
            ids.mapNotNull { byId[it] }
        }

    /**
     * 删除单条记忆。
     *
     * 返回 true 表示文件内容发生变化。
     */
    override suspend fun deleteItem(memoryId: MemoryId, itemId: String): Boolean =
        deleteItems(memoryId, listOf(itemId)) > 0

    /**
     * 批量删除记忆。
     *
     * 返回实际删除数量。
     */
    override suspend fun deleteItems(memoryId: MemoryId, ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        return mutex.withLock {
            val before = readLines<MemoryItem>(itemsFile)
            val after = before.filterNot { it.memoryId == memoryId && it.id in ids }
            writeLines(itemsFile, after)
            before.size - after.size
        }
    }

    /**
     * 统计当前记忆空间 item 数量。
     *
     * 用于容量预算和测试断言。
     */
    override suspend fun count(memoryId: MemoryId): Int =
        mutex.withLock {
            readLines<MemoryItem>(itemsFile).count { it.memoryId == memoryId }
        }

    /**
     * 保存或替换 Insight 节点。
     *
     * JSONL store 保持 upsert 行为，避免重复 flush 产生同 ID 多行。
     */
    override suspend fun saveInsight(node: InsightNode): String {
        mutex.withLock {
            val records = readLines<InsightNode>(insightsFile)
                .filterNot { it.memoryId == node.memoryId && it.id == node.id }
                .toMutableList()
            records.add(node)
            writeLines(insightsFile, records)
        }
        return node.id
    }

    /**
     * 读取当前记忆空间全部 Insight。
     *
     * 返回按创建时间排序的节点列表。
     */
    override suspend fun getInsights(memoryId: MemoryId): List<InsightNode> =
        mutex.withLock {
            readLines<InsightNode>(insightsFile)
                .filter { it.memoryId == memoryId }
                .sortedBy { it.createdAt }
        }

    /**
     * 按 ID 读取 Insight。
     *
     * 返回 null 表示不存在。
     */
    override suspend fun getInsightById(memoryId: MemoryId, insightId: String): InsightNode? =
        mutex.withLock {
            readLines<InsightNode>(insightsFile)
                .firstOrNull { it.memoryId == memoryId && it.id == insightId }
        }

    /**
     * 批量删除 Insight。
     *
     * 返回实际删除数量。
     */
    override suspend fun deleteInsights(memoryId: MemoryId, ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        return mutex.withLock {
            val before = readLines<InsightNode>(insightsFile)
            val after = before.filterNot { it.memoryId == memoryId && it.id in ids }
            writeLines(insightsFile, after)
            before.size - after.size
        }
    }

    /**
     * 保存或替换 RawData。
     *
     * RawData 用于原文回溯和后续 raw-data tier 检索。
     */
    override suspend fun saveRawData(rawData: RawData): String {
        mutex.withLock {
            val records = readLines<RawData>(rawDataFile)
                .filterNot { it.memoryId == rawData.memoryId && it.id == rawData.id }
                .toMutableList()
            records.add(rawData)
            writeLines(rawDataFile, records)
        }
        return rawData.id
    }

    /**
     * 获取 RawData ID 列表。
     *
     * ID 按创建时间升序返回。
     */
    override suspend fun getRawDataIds(memoryId: MemoryId): List<String> =
        mutex.withLock {
            readLines<RawData>(rawDataFile)
                .filter { it.memoryId == memoryId }
                .sortedBy { it.createdAt }
                .map { it.id }
        }

    /**
     * 按 ID 读取 RawData。
     *
     * 返回 null 表示不存在。
     */
    override suspend fun getRawData(memoryId: MemoryId, rawDataId: String): RawData? =
        mutex.withLock {
            readLines<RawData>(rawDataFile)
                .firstOrNull { it.memoryId == memoryId && it.id == rawDataId }
        }

    /**
     * 分页读取 RawData。
     *
     * 避免一次性加载过多原始文本。
     */
    override suspend fun getRawData(memoryId: MemoryId, limit: Int, offset: Int): List<RawData> =
        mutex.withLock {
            readLines<RawData>(rawDataFile)
                .filter { it.memoryId == memoryId }
                .sortedBy { it.createdAt }
                .drop(offset)
                .take(limit)
        }

    /**
     * 保存缓冲消息。
     *
     * pending/recent 通过 kind 区分，支持核心模块的 commit 和后续 getContext。
     */
    override suspend fun saveBufferMessage(message: BufferMessage): String {
        mutex.withLock {
            val records = readLines<BufferMessage>(buffersFile)
                .filterNot { it.memoryId == message.memoryId && it.id == message.id }
                .toMutableList()
            records.add(message)
            writeLines(buffersFile, records)
        }
        return message.id
    }

    /**
     * 读取缓冲消息。
     *
     * 返回指定 kind 的前 limit 条消息。
     */
    override suspend fun getBufferMessages(
        memoryId: MemoryId,
        kind: BufferKind,
        limit: Int,
    ): List<BufferMessage> =
        mutex.withLock {
            readLines<BufferMessage>(buffersFile)
                .filter { it.memoryId == memoryId && it.kind == kind }
                .sortedBy { it.createdAt }
                .take(limit)
        }

    /**
     * 清空缓冲区。
     *
     * commit drain pending buffer 后会调用该函数。
     */
    override suspend fun clearBuffer(memoryId: MemoryId, kind: BufferKind): Int =
        mutex.withLock {
            val before = readLines<BufferMessage>(buffersFile)
            val after = before.filterNot { it.memoryId == memoryId && it.kind == kind }
            writeLines(buffersFile, after)
            before.size - after.size
        }

    /**
     * 裁剪缓冲区。
     *
     * 保留最近 maxMessages 条消息，返回删除数量。
     */
    override suspend fun trimBuffer(memoryId: MemoryId, kind: BufferKind, maxMessages: Int): Int =
        mutex.withLock {
            if (maxMessages < 0) return@withLock 0
            val before = readLines<BufferMessage>(buffersFile)
            val target = before
                .filter { it.memoryId == memoryId && it.kind == kind }
                .sortedBy { it.createdAt }
            val keepIds = target.takeLast(maxMessages).map { it.id }.toSet()
            val after = before.filterNot {
                it.memoryId == memoryId && it.kind == kind && it.id !in keepIds
            }
            writeLines(buffersFile, after)
            before.size - after.size
        }

    /**
     * 记录派生结构重建原因。
     *
     * 当前仅写入 JSONL，后续 Insight/Thread 模块可读取该文件消费 dirty flag。
     */
    override suspend fun markRebuildRequired(memoryId: MemoryId, reason: String) {
        mutex.withLock {
            Files.writeString(
                rebuildFile,
                "${memoryId.toIdentifier()}\t$reason\n",
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND,
            )
        }
    }

    /**
     * 读取 JSONL 文件。
     *
     * 单行解析失败会被跳过，避免一个坏行破坏整个 store。
     */
    private inline fun <reified T> readLines(file: Path): List<T> {
        if (!Files.exists(file)) return emptyList()
        return Files.readAllLines(file)
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    json.decodeFromString<T>(line)
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            .toList()
    }

    /**
     * 重写 JSONL 文件。
     *
     * MVP 实现优先简单可靠，适合小规模本地记忆和测试持久化。
     */
    private inline fun <reified T> writeLines(file: Path, values: List<T>) {
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            values.joinToString(separator = "\n", postfix = if (values.isEmpty()) "" else "\n") {
                json.encodeToString(it)
            },
        )
    }
}
