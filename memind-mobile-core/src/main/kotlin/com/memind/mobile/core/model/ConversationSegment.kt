package com.memind.mobile.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
public data class ConversationSegment(
    val id: String = UUID.randomUUID().toString(),
    val memoryId: MemoryId,
    val messages: List<Message>,
    val sourceClient: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val startTime: Long? = messages.mapNotNull { it.timestamp }.minOrNull(),
    val endTime: Long? = messages.mapNotNull { it.timestamp }.maxOrNull(),
) {
    /**
     * 将对话段拼接成纯文本。
     *
     * 保留 role 前缀，方便后续抽取器区分用户和助手内容。
     */
    public fun textContent(): String =
        messages.joinToString("\n") { message -> "${message.role}: ${message.content}" }

    /**
     * 将对话段转换为 RawData。
     *
     * RawData 用于保存原始输入，并与后续抽取出的 MemoryItem 建立关联。
     */
    public fun toRawData(contentType: String = "conversation"): RawData =
        RawData(
            id = id,
            memoryId = memoryId,
            content = textContent(),
            contentType = contentType,
            sourceClient = sourceClient,
            metadata = metadata,
            startTime = startTime,
            endTime = endTime,
        )
}
