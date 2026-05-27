package com.memind.mobile.core.buffer

import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.Message
import com.memind.mobile.core.store.BufferKind
import com.memind.mobile.core.store.BufferMessage
import com.memind.mobile.core.store.MemoryStore

public class RecentConversationBuffer(
    private val store: MemoryStore,
    private val maxMessages: Int = 50,
) {
    /**
     * 追加近期消息。
     *
     * 写入后会裁剪到 maxMessages，避免移动端长期积累上下文缓冲。
     */
    public suspend fun append(memoryId: MemoryId, message: Message): String {
        val entry = BufferMessage(memoryId = memoryId, message = message, kind = BufferKind.RECENT)
        val id = store.saveBufferMessage(entry)
        store.trimBuffer(memoryId, BufferKind.RECENT, maxMessages)
        return id
    }

    /**
     * 读取近期消息。
     *
     * 返回按时间升序排列的最近 limit 条消息，用于后续 getContext 组装上下文。
     */
    public suspend fun load(memoryId: MemoryId, limit: Int = maxMessages): List<Message> =
        store.getBufferMessages(memoryId, BufferKind.RECENT, limit).map { it.toMessage() }
}
