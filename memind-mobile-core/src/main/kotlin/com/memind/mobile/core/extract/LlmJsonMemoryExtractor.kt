package com.memind.mobile.core.extract

import com.memind.mobile.core.llm.ChatClient
import com.memind.mobile.core.model.ConversationSegment
import com.memind.mobile.core.model.ExtractionConfig
import com.memind.mobile.core.model.ExtractionResult
import com.memind.mobile.core.model.MemoryCategory
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.MemoryItemType
import com.memind.mobile.core.model.RawData
import com.memind.mobile.core.search.TextSearch
import com.memind.mobile.core.store.MemoryItem
import com.memind.mobile.core.store.MemoryStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.withTimeoutOrNull

public class LlmJsonMemoryExtractor(
    private val chatClient: ChatClient,
    private val store: MemoryStore,
    private val textSearch: TextSearch,
    private val fallback: MemoryExtractor,
    private val deduplicator: MemoryDeduplicator = MemoryDeduplicator(store),
    private val temporalNormalizer: TemporalNormalizer = TemporalNormalizer(),
) : MemoryExtractor {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 从独立文本抽取记忆。
     *
     * 默认不启用 LLM；只有 config.enableLlmExtraction 为 true 时才调用远程模型。
     */
    override suspend fun extractText(
        memoryId: MemoryId,
        content: String,
        config: ExtractionConfig,
    ): ExtractionResult {
        if (!config.enableLlmExtraction) return fallback.extractText(memoryId, content, config)
        val rawData = RawData(
            memoryId = memoryId,
            content = content.take(config.maxInputChars),
            contentType = "text",
        )
        return extractWithLlm(rawData, rawData.content, emptyList(), config)
            ?: fallback.extractText(memoryId, content, config)
    }

    /**
     * 从对话段抽取记忆。
     *
     * LLM 失败、超时或返回非法 JSON 时会自动降级到规则 extractor。
     */
    override suspend fun extractSegment(
        segment: ConversationSegment,
        config: ExtractionConfig,
    ): ExtractionResult {
        if (!config.enableLlmExtraction) return fallback.extractSegment(segment, config)
        val content = segment.textContent().take(config.maxInputChars)
        val rawData = segment.toRawData().copy(content = content)
        return extractWithLlm(rawData, content, segment.messages, config)
            ?: fallback.extractSegment(segment, config)
    }

    /**
     * 调用 LLM 并保存结构化 item。
     *
     * 该函数只接受受控 JSON 结果，解析或保存失败会返回 null 触发 fallback。
     */
    private suspend fun extractWithLlm(
        rawData: RawData,
        content: String,
        messages: List<com.memind.mobile.core.model.Message>,
        config: ExtractionConfig,
    ): ExtractionResult? = runCatching {
        val prompt = buildPrompt(content, config)
        val response = withTimeoutOrNull(config.timeoutMs) {
            chatClient.chat(prompt, systemMessage(config))
        } ?: return@runCatching null
        val payload = parsePayload(response.content)
        val candidates = payload.items
            .asSequence()
            .mapNotNull { it.toCandidate(config) }
            .take(config.maxExtractedItems)
            .toList()
        if (candidates.isEmpty()) return@runCatching fallback.extractText(rawData.memoryId, content, config)

        store.saveRawData(rawData)
        val itemIds = mutableListOf<String>()
        candidates.forEachIndexed { index, candidate ->
            val hash = ExtractionSupport.sha256(candidate.text)
            val duplicate = deduplicator.findDuplicate(rawData.memoryId, config, hash, candidate.text)
            if (duplicate != null) {
                itemIds.add(duplicate.id)
                return@forEachIndexed
            }
            val now = System.currentTimeMillis()
            val normalizedTime = temporalNormalizer.normalize(
                text = candidate.text,
                messages = messages,
                observedAt = now,
            )
            val item = MemoryItem(
                id = "${rawData.memoryId.toIdentifier()}:$now:$index",
                memoryId = rawData.memoryId,
                text = candidate.text,
                scope = config.scope,
                category = candidate.category,
                contentType = rawData.contentType,
                sourceClient = rawData.sourceClient,
                source = "llm-json",
                rawDataId = rawData.id,
                contentHash = hash,
                occurredAt = normalizedTime.occurredAt,
                occurredStart = normalizedTime.occurredStart,
                occurredEnd = normalizedTime.occurredEnd,
                timeGranularity = normalizedTime.granularity,
                observedAt = now,
                metadata = candidate.metadata,
                timestamp = now,
                createdAt = now,
                type = candidate.type,
            )
            textSearch.index(rawData.memoryId, listOf(item))
            store.saveItem(item)
            deduplicator.indexIfEnabled(rawData.memoryId, item.id, item.text, config)
            itemIds.add(item.id)
        }
        ExtractionResult.success(rawData.memoryId).copy(
            rawDataId = rawData.id,
            itemIds = itemIds.distinct(),
            totalMemoryItems = itemIds.distinct().size,
        )
    }.getOrNull()

    /**
     * 构建 LLM 抽取 prompt。
     *
     * 明确限制输出 JSON、条数和分类集合，降低移动端解析成本。
     */
    private fun buildPrompt(content: String, config: ExtractionConfig): String =
        """
        Extract up to ${config.maxExtractedItems} durable memory items from the content.
        Language: ${config.language}
        Scope: ${config.scope.name}
        Allowed categories: ${allowedCategories(config).joinToString(",") { it.categoryName }}
        Return JSON only:
        {"items":[{"text":"...", "category":"event", "type":"fact", "metadata":{}}]}

        Content:
        ${content.take(config.maxInputChars)}
        """.trimIndent()

    /**
     * 构建系统提示。
     *
     * 要求模型只输出可解析 JSON，减少移动端 fallback 频率。
     */
    private fun systemMessage(config: ExtractionConfig): String =
        "You are a mobile memory extractor. Return compact JSON only. Do not include markdown. Foresight enabled: ${config.enableForesight}."

    /**
     * 从模型响应中解析 JSON。
     *
     * 兼容模型偶尔包裹解释文字的情况，只截取最外层对象。
     */
    private fun parsePayload(content: String): LlmExtractionPayload {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        require(start >= 0 && end >= start) { "No JSON object in LLM response" }
        return json.decodeFromString<LlmExtractionPayload>(content.substring(start, end + 1))
    }

    /**
     * 返回当前 scope 允许的分类。
     *
     * 避免 LLM 把 USER 记忆写入 AGENT 分类或反向写入。
     */
    private fun allowedCategories(config: ExtractionConfig): Set<MemoryCategory> =
        MemoryCategory.entries.filter { it.scope == config.scope }.toSet()

    @Serializable
    private data class LlmExtractionPayload(
        val items: List<LlmItem> = emptyList(),
    )

    @Serializable
    private data class LlmItem(
        val text: String = "",
        val category: String? = null,
        val type: String? = null,
        val metadata: Map<String, String> = emptyMap(),
    ) {
        /**
         * 转换为内部候选项。
         *
         * 非法分类会回退到当前 scope 默认分类，空文本会被丢弃。
         */
        fun toCandidate(config: ExtractionConfig): Candidate? {
            val cleanedText = text.trim()
            if (cleanedText.isBlank()) return null
            val parsedCategory = MemoryCategory.fromName(category)
                ?.takeIf { it.scope == config.scope }
                ?: ExtractionSupport.defaultCategory(config.scope)
            val parsedType = when (type?.lowercase()) {
                "foresight" -> if (config.enableForesight) MemoryItemType.FORESIGHT else MemoryItemType.FACT
                else -> MemoryItemType.FACT
            }
            return Candidate(cleanedText, parsedCategory, parsedType, metadata)
        }
    }

    private data class Candidate(
        val text: String,
        val category: MemoryCategory,
        val type: MemoryItemType,
        val metadata: Map<String, String>,
    )
}
