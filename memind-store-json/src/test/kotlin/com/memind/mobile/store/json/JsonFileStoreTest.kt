package com.memind.mobile.store.json

import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.Message
import com.memind.mobile.core.model.RawData
import com.memind.mobile.core.store.BufferKind
import com.memind.mobile.core.store.BufferMessage
import com.memind.mobile.core.store.MemoryItem
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JsonFileStoreTest {
    /**
     * 验证 JSON store 可以持久化并重新读取 item。
     *
     * 使用临时目录避免测试污染用户文件。
     */
    @Test
    fun `test persists memory item`() = runTest {
        val dir = Files.createTempDirectory("memind-json-store")
        val id = MemoryId.of("json-user", "json-agent")
        val store = JsonFileStore(dir)

        store.saveItem(MemoryItem(id = "item-1", memoryId = id, text = "hello json"))

        val reopened = JsonFileStore(dir)
        assertEquals("hello json", reopened.getItemById(id, "item-1")?.text)
    }

    /**
     * 验证 RawData 与缓冲消息路径。
     *
     * 这保护默认持久化模块对 commit 语义的支持。
     */
    @Test
    fun `test persists raw data and buffers`() = runTest {
        val dir = Files.createTempDirectory("memind-json-store")
        val id = MemoryId.of("json-user", "json-agent")
        val store = JsonFileStore(dir)

        store.saveRawData(RawData(id = "raw-1", memoryId = id, content = "raw"))
        store.saveBufferMessage(
            BufferMessage(
                id = "buffer-1",
                memoryId = id,
                message = Message.user("pending"),
                kind = BufferKind.PENDING,
            ),
        )

        assertNotNull(store.getRawData(id, "raw-1"))
        assertEquals(1, store.getBufferMessages(id, BufferKind.PENDING).size)
        assertEquals(1, store.clearBuffer(id, BufferKind.PENDING))
        assertEquals(0, store.getBufferMessages(id, BufferKind.PENDING).size)
    }
}
