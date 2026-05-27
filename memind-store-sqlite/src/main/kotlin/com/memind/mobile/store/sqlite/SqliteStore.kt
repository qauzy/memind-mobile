package com.memind.mobile.store.sqlite

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
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

public class SqliteStore(
    private val databasePath: Path,
) : MemoryStore {
    private val mutex = Mutex()
    private val jdbcUrl = "jdbc:sqlite:${databasePath.toAbsolutePath()}"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        databasePath.parent?.let { Files.createDirectories(it) }
        Class.forName("org.sqlite.JDBC")
        connection().use { conn ->
            conn.createStatement().use { statement ->
                statement.executeUpdate("PRAGMA journal_mode=WAL")
                statement.executeUpdate("PRAGMA foreign_keys=ON")
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS memory_items (
                        memory_id TEXT NOT NULL,
                        id TEXT NOT NULL,
                        json TEXT NOT NULL,
                        scope TEXT NOT NULL,
                        category TEXT,
                        type TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY (memory_id, id)
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS raw_data (
                        memory_id TEXT NOT NULL,
                        id TEXT NOT NULL,
                        json TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY (memory_id, id)
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS insights (
                        memory_id TEXT NOT NULL,
                        id TEXT NOT NULL,
                        json TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY (memory_id, id)
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS buffer_messages (
                        memory_id TEXT NOT NULL,
                        id TEXT NOT NULL,
                        json TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY (memory_id, id)
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS rebuild_flags (
                        memory_id TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_memory_items_filter ON memory_items(memory_id, scope, category, type, created_at)")
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_raw_data_memory ON raw_data(memory_id, created_at)")
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_insights_memory ON insights(memory_id, created_at)")
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_buffer_messages_filter ON buffer_messages(memory_id, kind, created_at)")
            }
        }
    }

    /**
     * 保存或替换结构化记忆。
     *
     * SQLite 模块把可查询字段单独建列，完整领域对象仍以 JSON 保存，便于模型演进。
     */
    override suspend fun saveItem(item: MemoryItem): String {
        mutex.withLock {
            executeUpdate(
                """
                INSERT OR REPLACE INTO memory_items(memory_id, id, json, scope, category, type, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                listOf(
                    item.memoryId.toIdentifier(),
                    item.id,
                    json.encodeToString(item),
                    item.scope.name,
                    item.category?.name,
                    item.type.name,
                    item.createdAt,
                ),
            )
        }
        return item.id
    }

    /**
     * 分页读取结构化记忆。
     *
     * scope/category/type 会下推到 SQLite 查询，避免大数据量时全量反序列化。
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
            val clauses = mutableListOf("memory_id = ?")
            val params = mutableListOf<Any?>(memoryId.toIdentifier())
            if (scope != null) {
                clauses.add("scope = ?")
                params.add(scope.name)
            }
            if (!categories.isNullOrEmpty()) {
                clauses.add("category IN (${categories.joinToString { "?" }})")
                params.addAll(categories.map { it.name })
            }
            if (type != null) {
                clauses.add("type = ?")
                params.add(type.name)
            }
            params.add(limit)
            params.add(offset)
            queryJson<MemoryItem>(
                """
                SELECT json FROM memory_items
                WHERE ${clauses.joinToString(" AND ")}
                ORDER BY created_at ASC
                LIMIT ? OFFSET ?
                """.trimIndent(),
                params,
            )
        }

    /**
     * 按 ID 读取单条记忆。
     *
     * 返回 null 表示 SQLite 中没有对应 memoryId 和 itemId。
     */
    override suspend fun getItemById(memoryId: MemoryId, itemId: String): MemoryItem? =
        mutex.withLock {
            queryJson<MemoryItem>(
                "SELECT json FROM memory_items WHERE memory_id = ? AND id = ?",
                listOf(memoryId.toIdentifier(), itemId),
            ).firstOrNull()
        }

    /**
     * 按入参 ID 顺序批量读取记忆。
     *
     * SQLite 查询后再按 ids 回填，保持向量召回等上游排序。
     */
    override suspend fun getItemsByIds(memoryId: MemoryId, ids: List<String>): List<MemoryItem> {
        if (ids.isEmpty()) return emptyList()
        return mutex.withLock {
            val params = mutableListOf<Any?>(memoryId.toIdentifier())
            params.addAll(ids)
            val records = queryJson<MemoryItem>(
                """
                SELECT json FROM memory_items
                WHERE memory_id = ? AND id IN (${ids.joinToString { "?" }})
                """.trimIndent(),
                params,
            ).associateBy { it.id }
            ids.mapNotNull { records[it] }
        }
    }

    /**
     * 删除单条记忆。
     *
     * 返回 true 表示 SQLite 实际删除了一行。
     */
    override suspend fun deleteItem(memoryId: MemoryId, itemId: String): Boolean =
        deleteItems(memoryId, listOf(itemId)) > 0

    /**
     * 批量删除记忆。
     *
     * 使用单条 DELETE IN 语句删除，返回 SQLite 报告的受影响行数。
     */
    override suspend fun deleteItems(memoryId: MemoryId, ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        return mutex.withLock {
            val params = mutableListOf<Any?>(memoryId.toIdentifier())
            params.addAll(ids)
            executeUpdate(
                "DELETE FROM memory_items WHERE memory_id = ? AND id IN (${ids.joinToString { "?" }})",
                params,
            )
        }
    }

    /**
     * 统计当前记忆空间的 item 数量。
     *
     * count 查询不反序列化 JSON，适合容量预算和测试断言。
     */
    override suspend fun count(memoryId: MemoryId): Int =
        mutex.withLock {
            queryLong(
                "SELECT COUNT(*) FROM memory_items WHERE memory_id = ?",
                listOf(memoryId.toIdentifier()),
            ).toInt()
        }

    /**
     * 保存或替换 Insight 节点。
     *
     * 当前保留完整 JSON，后续 Insight schema 升级时不用迁移所有列。
     */
    override suspend fun saveInsight(node: InsightNode): String {
        mutex.withLock {
            executeUpdate(
                """
                INSERT OR REPLACE INTO insights(memory_id, id, json, created_at)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
                listOf(node.memoryId.toIdentifier(), node.id, json.encodeToString(node), node.createdAt),
            )
        }
        return node.id
    }

    /**
     * 读取当前记忆空间的 Insight 列表。
     *
     * 返回按创建时间升序排列的节点。
     */
    override suspend fun getInsights(memoryId: MemoryId): List<InsightNode> =
        mutex.withLock {
            queryJson<InsightNode>(
                "SELECT json FROM insights WHERE memory_id = ? ORDER BY created_at ASC",
                listOf(memoryId.toIdentifier()),
            )
        }

    /**
     * 按 ID 读取 Insight。
     *
     * 返回 null 表示 SQLite 中不存在该节点。
     */
    override suspend fun getInsightById(memoryId: MemoryId, insightId: String): InsightNode? =
        mutex.withLock {
            queryJson<InsightNode>(
                "SELECT json FROM insights WHERE memory_id = ? AND id = ?",
                listOf(memoryId.toIdentifier(), insightId),
            ).firstOrNull()
        }

    /**
     * 批量删除 Insight。
     *
     * 返回实际删除数量。
     */
    override suspend fun deleteInsights(memoryId: MemoryId, ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        return mutex.withLock {
            val params = mutableListOf<Any?>(memoryId.toIdentifier())
            params.addAll(ids)
            executeUpdate(
                "DELETE FROM insights WHERE memory_id = ? AND id IN (${ids.joinToString { "?" }})",
                params,
            )
        }
    }

    /**
     * 保存或替换 RawData。
     *
     * RawData 主要用于原文回溯和后续 raw-data tier 检索。
     */
    override suspend fun saveRawData(rawData: RawData): String {
        mutex.withLock {
            executeUpdate(
                """
                INSERT OR REPLACE INTO raw_data(memory_id, id, json, created_at)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
                listOf(rawData.memoryId.toIdentifier(), rawData.id, json.encodeToString(rawData), rawData.createdAt),
            )
        }
        return rawData.id
    }

    /**
     * 获取 RawData ID 列表。
     *
     * ID 按创建时间升序返回，便于调试和分页索引。
     */
    override suspend fun getRawDataIds(memoryId: MemoryId): List<String> =
        mutex.withLock {
            queryStrings(
                "SELECT id FROM raw_data WHERE memory_id = ? ORDER BY created_at ASC",
                listOf(memoryId.toIdentifier()),
            )
        }

    /**
     * 按 ID 获取 RawData。
     *
     * 返回 null 表示没有该原始输入。
     */
    override suspend fun getRawData(memoryId: MemoryId, rawDataId: String): RawData? =
        mutex.withLock {
            queryJson<RawData>(
                "SELECT json FROM raw_data WHERE memory_id = ? AND id = ?",
                listOf(memoryId.toIdentifier(), rawDataId),
            ).firstOrNull()
        }

    /**
     * 分页读取 RawData。
     *
     * SQLite 使用 LIMIT/OFFSET 控制读取规模，避免一次性加载过多原文。
     */
    override suspend fun getRawData(memoryId: MemoryId, limit: Int, offset: Int): List<RawData> =
        mutex.withLock {
            queryJson<RawData>(
                """
                SELECT json FROM raw_data
                WHERE memory_id = ?
                ORDER BY created_at ASC
                LIMIT ? OFFSET ?
                """.trimIndent(),
                listOf(memoryId.toIdentifier(), limit, offset),
            )
        }

    /**
     * 保存或替换缓冲消息。
     *
     * kind 单独建列，支撑 pending/recent 的快速读取和清理。
     */
    override suspend fun saveBufferMessage(message: BufferMessage): String {
        mutex.withLock {
            executeUpdate(
                """
                INSERT OR REPLACE INTO buffer_messages(memory_id, id, json, kind, created_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                listOf(
                    message.memoryId.toIdentifier(),
                    message.id,
                    json.encodeToString(message),
                    message.kind.name,
                    message.createdAt,
                ),
            )
        }
        return message.id
    }

    /**
     * 读取指定 kind 的缓冲消息。
     *
     * 返回按创建时间升序排列的最多 limit 条记录。
     */
    override suspend fun getBufferMessages(
        memoryId: MemoryId,
        kind: BufferKind,
        limit: Int,
    ): List<BufferMessage> =
        mutex.withLock {
            queryJson<BufferMessage>(
                """
                SELECT json FROM buffer_messages
                WHERE memory_id = ? AND kind = ?
                ORDER BY created_at ASC
                LIMIT ?
                """.trimIndent(),
                listOf(memoryId.toIdentifier(), kind.name, limit),
            )
        }

    /**
     * 清空指定 kind 的缓冲区。
     *
     * commit drain pending buffer 后会调用该函数。
     */
    override suspend fun clearBuffer(memoryId: MemoryId, kind: BufferKind): Int =
        mutex.withLock {
            executeUpdate(
                "DELETE FROM buffer_messages WHERE memory_id = ? AND kind = ?",
                listOf(memoryId.toIdentifier(), kind.name),
            )
        }

    /**
     * 裁剪指定 kind 的缓冲区。
     *
     * 保留最近 maxMessages 条，返回被删除的旧消息数量。
     */
    override suspend fun trimBuffer(memoryId: MemoryId, kind: BufferKind, maxMessages: Int): Int =
        mutex.withLock {
            if (maxMessages < 0) return@withLock 0
            val allIds = queryStrings(
                """
                SELECT id FROM buffer_messages
                WHERE memory_id = ? AND kind = ?
                ORDER BY created_at ASC
                """.trimIndent(),
                listOf(memoryId.toIdentifier(), kind.name),
            )
            val deleteIds = allIds.dropLast(maxMessages)
            if (deleteIds.isEmpty()) {
                0
            } else {
                val params = mutableListOf<Any?>(memoryId.toIdentifier(), kind.name)
                params.addAll(deleteIds)
                executeUpdate(
                    "DELETE FROM buffer_messages WHERE memory_id = ? AND kind = ? AND id IN (${deleteIds.joinToString { "?" }})",
                    params,
                )
            }
        }

    /**
     * 标记派生结构需要重建。
     *
     * 当前写入 SQLite flags 表，后续 Insight/Thread 模块可以消费该记录。
     */
    override suspend fun markRebuildRequired(memoryId: MemoryId, reason: String) {
        mutex.withLock {
            executeUpdate(
                "INSERT INTO rebuild_flags(memory_id, reason, created_at) VALUES (?, ?, ?)",
                listOf(memoryId.toIdentifier(), reason, System.currentTimeMillis()),
            )
        }
    }

    /**
     * 打开 SQLite 连接。
     *
     * 每次操作使用短连接，配合 mutex 保证当前 store 实例内的写入顺序。
     */
    private fun connection(): Connection = DriverManager.getConnection(jdbcUrl)

    /**
     * 执行更新语句。
     *
     * 返回 SQLite 报告的受影响行数，用于 delete/clear 语义。
     */
    private fun executeUpdate(sql: String, params: List<Any?>): Int =
        connection().use { conn ->
            conn.prepareStatement(sql).use { statement ->
                statement.bind(params)
                statement.executeUpdate()
            }
        }

    /**
     * 查询 JSON 列并反序列化为领域对象。
     *
     * 单条坏数据会被跳过，避免一行损坏破坏整个存储读取。
     */
    private inline fun <reified T> queryJson(sql: String, params: List<Any?>): List<T> =
        connection().use { conn ->
            conn.prepareStatement(sql).use { statement ->
                statement.bind(params)
                statement.executeQuery().use { rows ->
                    val values = mutableListOf<T>()
                    while (rows.next()) {
                        try {
                            values.add(json.decodeFromString(rows.getString("json")))
                        } catch (_: SerializationException) {
                            // 坏行跳过，保持存储尽可能可读。
                        } catch (_: IllegalArgumentException) {
                            // 兼容枚举或字段非法导致的反序列化异常。
                        }
                    }
                    values
                }
            }
        }

    /**
     * 查询第一列字符串列表。
     *
     * 用于 RawData ID、buffer ID 等轻量索引读取。
     */
    private fun queryStrings(sql: String, params: List<Any?>): List<String> =
        connection().use { conn ->
            conn.prepareStatement(sql).use { statement ->
                statement.bind(params)
                statement.executeQuery().use { rows ->
                    val values = mutableListOf<String>()
                    while (rows.next()) {
                        values.add(rows.getString(1))
                    }
                    values
                }
            }
        }

    /**
     * 查询单个 Long 值。
     *
     * 用于 count 这类聚合查询。
     */
    private fun queryLong(sql: String, params: List<Any?>): Long =
        connection().use { conn ->
            conn.prepareStatement(sql).use { statement ->
                statement.bind(params)
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.getLong(1) else 0L
                }
            }
        }

    /**
     * 绑定 PreparedStatement 参数。
     *
     * 统一处理 null、String、Int、Long，避免 SQL 拼接带来的注入风险。
     */
    private fun PreparedStatement.bind(params: List<Any?>) {
        params.forEachIndexed { index, value ->
            val column = index + 1
            when (value) {
                null -> setObject(column, null)
                is Int -> setInt(column, value)
                is Long -> setLong(column, value)
                else -> setString(column, value.toString())
            }
        }
    }
}
