package com.memind.mobile.core

import com.memind.mobile.core.insight.InsightBuilder
import com.memind.mobile.core.insight.InsightTree
import com.memind.mobile.core.buffer.CommitDetector
import com.memind.mobile.core.buffer.LocalRuleCommitDetector
import com.memind.mobile.core.buffer.PendingConversationBuffer
import com.memind.mobile.core.buffer.RecentConversationBuffer
import com.memind.mobile.core.extract.MemoryExtractor
import com.memind.mobile.core.extract.LlmJsonMemoryExtractor
import com.memind.mobile.core.extract.MemoryDeduplicator
import com.memind.mobile.core.extract.RuleBasedMemoryExtractor
import com.memind.mobile.core.llm.ChatClient
import com.memind.mobile.core.model.AddResult
import com.memind.mobile.core.model.AddStatus
import com.memind.mobile.core.model.ConversationSegment
import com.memind.mobile.core.model.ExtractionConfig
import com.memind.mobile.core.model.ExtractionResult
import com.memind.mobile.core.model.HealthStatus
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.Message
import com.memind.mobile.core.model.RetrievalConfig
import com.memind.mobile.core.model.RetrievalRequest
import com.memind.mobile.core.model.RetrievalResult
import com.memind.mobile.core.model.RetrievalStatus
import com.memind.mobile.core.model.Strategy
import com.memind.mobile.core.search.SimpleTextSearch
import com.memind.mobile.core.search.TextSearch
import com.memind.mobile.core.search.VectorSearch
import com.memind.mobile.core.store.MemoryStore
import com.memind.mobile.core.store.InMemoryStore
import java.util.concurrent.atomic.AtomicBoolean

public class DefaultMemory(
    private val chatClient: ChatClient,
    private val store: MemoryStore = InMemoryStore(),
    private val textSearch: TextSearch = SimpleTextSearch(),
    private val vectorSearch: VectorSearch? = null,
    private val insightBuilder: InsightBuilder = InsightBuilder(store),
    private val commitDetector: CommitDetector = LocalRuleCommitDetector(),
    private val extractor: MemoryExtractor = LlmJsonMemoryExtractor(
        chatClient = chatClient,
        store = store,
        textSearch = textSearch,
        fallback = RuleBasedMemoryExtractor(
            store = store,
            textSearch = textSearch,
            deduplicator = MemoryDeduplicator(store, vectorSearch, chatClient),
        ),
        deduplicator = MemoryDeduplicator(store, vectorSearch, chatClient),
    ),
) : Memory {
    private val closed = AtomicBoolean(false)
    private val pendingBuffer = PendingConversationBuffer(store)
    private val recentBuffer = RecentConversationBuffer(store)

    /**
     * 添加单条消息到当前记忆空间的对话缓冲区。
     *
     * 阶段 3 开始先写 pending/recent buffer，真正的 MemoryItem 由 commit 统一生成。
     */
    override suspend fun addMessage(
        id: MemoryId,
        message: Message,
        config: ExtractionConfig,
    ): AddResult {
        if (closed.get()) return AddResult(AddStatus.FAILED, errorMessage = "closed")
        val messageId = pendingBuffer.append(id, message)
        recentBuffer.append(id, message)
        val pendingMessages = pendingBuffer.load(id)
        if (commitDetector.shouldCommit(pendingMessages, message)) {
            val extraction = commit(id, config)
            return AddResult(
                status = if (extraction.isSuccess) AddStatus.EXTRACTED else AddStatus.FAILED,
                messageId = messageId,
                rawDataId = extraction.rawDataId,
                errorMessage = extraction.errorMessage,
            )
        }
        return AddResult(AddStatus.BUFFERED, messageId = messageId)
    }

    /**
     * 批量添加消息到当前记忆空间的缓冲区。
     *
     * 当前按顺序复用单条添加逻辑，保持 pending 和 recent 缓冲顺序一致。
     */
    override suspend fun addMessages(
        id: MemoryId,
        messages: List<Message>,
        config: ExtractionConfig,
    ): AddResult {
        var lastMessageId: String? = null
        messages.forEach { lastMessageId = addMessage(id, it, config).messageId }
        return AddResult(AddStatus.BUFFERED, messageId = lastMessageId)
    }

    /**
     * 从独立文本中抽取记忆。
     *
     * 该函数会同时写入 RawData 和对应的轻量 MemoryItem，为后续 raw/item/insight 三层检索打基础。
     */
    override suspend fun extract(
        id: MemoryId,
        content: String,
        config: ExtractionConfig,
    ): ExtractionResult {
        return extractor.extractText(id, content, config)
    }

    /**
     * 提交当前记忆空间的待处理消息。
     *
     * 会使用默认配置 drain pending buffer，并生成可检索的对话段记忆。
     */
    override suspend fun commit(id: MemoryId): ExtractionResult {
        return commit(id, ExtractionConfig.defaults())
    }

    /**
     * 使用指定抽取配置提交待处理消息。
     *
     * 当前阶段使用轻量规则抽取：整个 ConversationSegment 保存为 RawData，并生成一个 MemoryItem。
     */
    override suspend fun commit(id: MemoryId, config: ExtractionConfig): ExtractionResult {
        if (closed.get()) return ExtractionResult.failed(id, "closed")
        val messages = pendingBuffer.drain(id)
        if (messages.isEmpty()) return ExtractionResult.success(id)
        val segment = ConversationSegment(
            memoryId = id,
            messages = messages,
            sourceClient = messages.firstNotNullOfOrNull { it.metadata["sourceClient"] },
            metadata = mergeMetadata(messages),
        )
        return extractor.extractSegment(segment, config)
    }

    /**
     * 使用兼容旧 API 的参数执行检索。
     *
     * 内部转换为 RetrievalRequest，统一走可扩展的 scope/category 检索入口。
     */
    override suspend fun retrieve(
        id: MemoryId,
        query: String,
        strategy: Strategy,
        config: RetrievalConfig,
    ): RetrievalResult {
        return retrieve(
            RetrievalRequest(
                memoryId = id,
                query = query,
                config = config.withStrategy(strategy),
            ),
        )
    }

    /**
     * 使用完整检索请求执行轻量检索。
     *
     * 当前支持文本搜索、最低分过滤、scope 过滤和 category 过滤；后续会接入 hybrid retrieval。
     */
    override suspend fun retrieve(request: RetrievalRequest): RetrievalResult {
        val id = request.memoryId
        val query = request.query
        val strategy = request.config.strategy
        val config = request.config
        if (query.isBlank()) return RetrievalResult.empty("$strategy", query)

        // 阶段 2 默认走关键词检索，后续会在这里接入向量和 insight 的混合召回。
        val searchResults = textSearch.search(id, query, limit = config.maxResults)
        val categoryFilter = request.categories.orEmpty()

        // 过滤顺序保持轻量：先按分数裁剪，再按 scope/category 过滤，减少后续结果映射成本。
        val resultItems = searchResults
            .filter { it.score >= config.minScore }
            .filter { request.scope == null || it.item.scope == request.scope }
            .filter { categoryFilter.isEmpty() || it.item.category in categoryFilter }
        val result = RetrievalResult(
            items = resultItems.map { scored ->
                val item = scored.item
                RetrievalResult.ScoredItem(
                    id = item.id,
                    text = item.text,
                    score = scored.score,
                    category = item.category?.categoryName,
                    source = item.source ?: item.sourceClient,
                    scope = item.scope,
                    rawDataId = item.rawDataId,
                    occurredAt = item.occurredAt,
                    metadata = item.metadata,
                )
            },
            strategy = strategy.name,
            query = query,
            status = if (resultItems.isEmpty()) RetrievalStatus.EMPTY else RetrievalStatus.SUCCESS,
        )
        return result
    }

    /**
     * 获取当前记忆空间的 Insight Tree。
     *
     * 阶段 2 仍使用轻量构建器；阶段 6 会改为增量 dirty flag 和后台/手动 flush。
     */
    override suspend fun getInsightTree(id: MemoryId): InsightTree {
        return insightBuilder.buildTree(id)
    }

    /**
     * 刷新 Insight 构建结果。
     *
     * 当前 Insight 是按需构建，因此该函数暂时不执行额外操作。
     */
    override suspend fun flushInsights(id: MemoryId) {
        // 当前 Insight 按需构建，暂不做额外刷新；后续阶段会落地 dirty flag。
    }

    /**
     * 检查记忆组件健康状态。
     *
     * 目前以 ChatClient 可用性作为降级信号，便于宿主 App 判断远程增强能力是否可用。
     */
    override suspend fun health(): HealthStatus {
        val llmOk = chatClient.health()
        return HealthStatus(
            status = if (llmOk) "UP" else "DEGRADED",
            message = if (llmOk) "All systems operational" else "LLM client unavailable",
        )
    }

    /**
     * 关闭当前 Memory 实例。
     *
     * 使用原子标记保证重复关闭不会产生副作用。
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
    }

    /**
     * 合并消息 metadata。
     *
     * 后写入的同名字段覆盖早期字段，便于 sourceClient 等实时消息元数据透传到提交结果。
     */
    private fun mergeMetadata(messages: List<Message>): Map<String, String> =
        buildMap {
            messages.forEach { putAll(it.metadata) }
        }

}
