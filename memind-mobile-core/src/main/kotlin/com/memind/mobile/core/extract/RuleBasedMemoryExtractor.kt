package com.memind.mobile.core.extract

import com.memind.mobile.core.model.ConversationSegment
import com.memind.mobile.core.model.ExtractionConfig
import com.memind.mobile.core.model.ExtractionResult
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.RawData
import com.memind.mobile.core.search.TextSearch
import com.memind.mobile.core.store.MemoryItem
import com.memind.mobile.core.store.MemoryStore

public class RuleBasedMemoryExtractor(
    private val store: MemoryStore,
    private val textSearch: TextSearch,
    private val deduplicator: MemoryDeduplicator = MemoryDeduplicator(store),
    private val temporalNormalizer: TemporalNormalizer = TemporalNormalizer(),
) : MemoryExtractor {
    /**
     * 从独立文本抽取记忆。
     *
     * 当前规则 extractor 不调用 LLM，会保存 RawData 并生成一个可检索 MemoryItem。
     */
    override suspend fun extractText(
        memoryId: MemoryId,
        content: String,
        config: ExtractionConfig,
    ): ExtractionResult {
        val now = System.currentTimeMillis()
        val rawData = RawData(
            memoryId = memoryId,
            content = content.take(config.maxInputChars),
            contentType = "text",
            createdAt = now,
        )
        val normalizedTime = temporalNormalizer.normalize(rawData.content, observedAt = now)
        return saveRawDataAsItem(
            rawData = rawData,
            text = rawData.content,
            config = config,
            source = "text",
            sourceClient = null,
            metadata = emptyMap(),
            normalizedTime = normalizedTime,
            timestamp = now,
        )
    }

    /**
     * 从对话段抽取记忆。
     *
     * 阶段 4 的离线 fallback 先把对话段整体作为一个 item，后续会拆成多条结构化事实。
     */
    override suspend fun extractSegment(
        segment: ConversationSegment,
        config: ExtractionConfig,
    ): ExtractionResult {
        val now = System.currentTimeMillis()
        val rawData = segment.toRawData()
        val clippedText = segment.textContent().take(config.maxInputChars)
        val normalizedTime = temporalNormalizer.normalize(
            text = clippedText,
            messages = segment.messages,
            observedAt = now,
        )
        return saveRawDataAsItem(
            rawData = rawData.copy(content = clippedText),
            text = clippedText,
            config = config,
            source = "conversation",
            sourceClient = segment.sourceClient,
            metadata = segment.metadata,
            normalizedTime = normalizedTime,
            timestamp = segment.endTime ?: now,
        )
    }

    /**
     * 保存 RawData 并生成 MemoryItem。
     *
     * 这里包含 hash 去重的最小实现，避免相同内容在同一 scope/category 下重复写入。
     */
    private suspend fun saveRawDataAsItem(
        rawData: RawData,
        text: String,
        config: ExtractionConfig,
        source: String,
        sourceClient: String?,
        metadata: Map<String, String>,
        normalizedTime: NormalizedTime,
        timestamp: Long,
    ): ExtractionResult {
        val contentHash = ExtractionSupport.sha256(text)
        val duplicated = deduplicator.findDuplicate(rawData.memoryId, config, contentHash, text)
        if (duplicated != null) {
            return ExtractionResult.success(rawData.memoryId).copy(
                itemIds = listOf(duplicated.id),
                totalMemoryItems = 0,
            )
        }

        val now = System.currentTimeMillis()
        store.saveRawData(rawData)
        // 规则 extractor 只生成一条 item，保证离线和弱网环境下仍可形成可检索记忆。
        val item = MemoryItem(
            id = "${rawData.memoryId.toIdentifier()}:$now",
            memoryId = rawData.memoryId,
            text = text,
            scope = config.scope,
            category = ExtractionSupport.defaultCategory(config.scope),
            contentType = rawData.contentType,
            sourceClient = sourceClient,
            source = source,
            rawDataId = rawData.id,
            contentHash = contentHash,
            occurredAt = normalizedTime.occurredAt,
            occurredStart = normalizedTime.occurredStart,
            occurredEnd = normalizedTime.occurredEnd,
            timeGranularity = normalizedTime.granularity,
            observedAt = timestamp,
            metadata = metadata,
            timestamp = timestamp,
            createdAt = now,
            type = if (config.enableForesight) {
                com.memind.mobile.core.model.MemoryItemType.FORESIGHT
            } else {
                com.memind.mobile.core.model.MemoryItemType.FACT
            },
        )
        textSearch.index(rawData.memoryId, listOf(item))
        store.saveItem(item)
        deduplicator.indexIfEnabled(rawData.memoryId, item.id, text, config)
        return ExtractionResult.success(rawData.memoryId).copy(
            rawDataId = rawData.id,
            itemIds = listOf(item.id),
            totalMemoryItems = 1,
        )
    }
}
