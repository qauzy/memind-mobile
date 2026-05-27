package com.memind.mobile.core.store

import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.Message
import java.util.UUID

public data class BufferMessage(
    val id: String = UUID.randomUUID().toString(),
    val memoryId: MemoryId,
    val message: Message,
    val kind: BufferKind,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /**
     * 返回缓冲消息中的原始 Message。
     *
     * BufferMessage 只补充 memoryId、kind 和写入时间，不改变宿主传入的消息内容。
     */
    public fun toMessage(): Message = message
}

public enum class BufferKind {
    PENDING,
    RECENT,
}
