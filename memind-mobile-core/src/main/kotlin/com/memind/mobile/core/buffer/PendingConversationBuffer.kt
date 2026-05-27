package com.memind.mobile.core.buffer

import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.Message
import com.memind.mobile.core.store.BufferKind
import com.memind.mobile.core.store.BufferMessage
import com.memind.mobile.core.store.MemoryStore

public class PendingConversationBuffer(
    private val store: MemoryStore,
) {
    /**
     * 追加待提交消息。
     *
     * memoryId 用于隔离用户/Agent 记忆空间，message 会原样保存以便 commit 时抽取。
     */
    public suspend fun append(memoryId: MemoryId, message: Message): String {
        val entry = BufferMessage(memoryId = memoryId, message = message, kind = BufferKind.PENDING)
        return store.saveBufferMessage(entry)
    }

    /**
     * 读取待提交消息但不清空。
     *
     * 边界检测会使用该函数判断是否需要自动 commit。
     */
    public suspend fun load(memoryId: MemoryId, limit: Int = Int.MAX_VALUE): List<Message> =
        store.getBufferMessages(memoryId, BufferKind.PENDING, limit).map { it.toMessage() }

    /**
     * 读取并清空 pending 缓冲区。
     *
     * 返回按写入顺序排列的消息列表，供 commit 生成 ConversationSegment。
     */
    public suspend fun drain(memoryId: MemoryId, limit: Int = Int.MAX_VALUE): List<Message> {
        val entries = store.getBufferMessages(memoryId, BufferKind.PENDING, limit)
        if (entries.isNotEmpty()) {
            store.clearBuffer(memoryId, BufferKind.PENDING)
        }
        return entries.map { it.toMessage() }
    }
}
