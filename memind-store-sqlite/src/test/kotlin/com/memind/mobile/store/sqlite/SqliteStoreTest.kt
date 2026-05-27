package com.memind.mobile.store.sqlite

import com.memind.mobile.core.model.MemoryCategory
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.Message
import com.memind.mobile.core.model.RawData
import com.memind.mobile.core.store.BufferKind
import com.memind.mobile.core.store.BufferMessage
import com.memind.mobile.core.store.InsightNode
import com.memind.mobile.core.store.InsightTier
import com.memind.mobile.core.store.MemoryItem
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqliteStoreTest {
    /**
     * 验证 SQLite store 可以持久化并重新读取 item。
     *
     * 使用临时数据库文件，避免测试污染用户目录。
     */
    @Test
    fun `test persists memory item`() = runTest {
        val db = Files.createTempDirectory("memind-sqlite-store").resolve("memory.db")
        val id = MemoryId.of("sqlite-user", "sqlite-agent")
        val store = SqliteStore(db)

        store.saveItem(
            MemoryItem(
                id = "item-1",
                memoryId = id,
                text = "hello sqlite",
                category = MemoryCategory.EVENT,
            ),
        )

        val reopened = SqliteStore(db)
        assertEquals("hello sqlite", reopened.getItemById(id, "item-1")?.text)
        assertEquals(1, reopened.getItems(id, categories = setOf(MemoryCategory.EVENT)).size)
    }

    /**
     * 验证 RawData、Insight 与缓冲消息路径。
     *
     * 这保护默认之外的 SQLite 持久化模块能覆盖 MemoryStore 基础契约。
     */
    @Test
    fun `test persists raw data insights and buffers`() = runTest {
        val db = Files.createTempDirectory("memind-sqlite-store").resolve("memory.db")
        val id = MemoryId.of("sqlite-user", "sqlite-agent")
        val store = SqliteStore(db)

        store.saveRawData(RawData(id = "raw-1", memoryId = id, content = "raw"))
        store.saveInsight(InsightNode(id = "insight-1", memoryId = id, text = "leaf", tier = InsightTier.LEAF))
        store.saveBufferMessage(
            BufferMessage(
                id = "buffer-1",
                memoryId = id,
                message = Message.user("pending"),
                kind = BufferKind.PENDING,
            ),
        )

        assertNotNull(store.getRawData(id, "raw-1"))
        assertEquals(listOf("raw-1"), store.getRawDataIds(id))
        assertEquals("leaf", store.getInsightById(id, "insight-1")?.text)
        assertEquals(1, store.getBufferMessages(id, BufferKind.PENDING).size)
        assertEquals(1, store.clearBuffer(id, BufferKind.PENDING))
        assertTrue(store.getBufferMessages(id, BufferKind.PENDING).isEmpty())
    }
}
