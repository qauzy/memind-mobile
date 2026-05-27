package com.memind.mobile.core.extract

import com.memind.mobile.core.model.ConversationSegment
import com.memind.mobile.core.model.ExtractionConfig
import com.memind.mobile.core.model.ExtractionResult
import com.memind.mobile.core.model.MemoryId

public interface MemoryExtractor {
    /**
     * 从独立文本抽取记忆。
     *
     * memoryId 标识目标记忆空间，content 是原始输入，config 控制 scope、语言和 insight 开关。
     */
    public suspend fun extractText(
        memoryId: MemoryId,
        content: String,
        config: ExtractionConfig,
    ): ExtractionResult

    /**
     * 从对话段抽取记忆。
     *
     * segment 保留消息、时间和 metadata，适合 commit 从 pending buffer 生成结构化记忆。
     */
    public suspend fun extractSegment(
        segment: ConversationSegment,
        config: ExtractionConfig,
    ): ExtractionResult
}
