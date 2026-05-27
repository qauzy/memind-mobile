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
import com.memind.mobile.core.model.ContextRequest
import com.memind.mobile.core.model.ContextWindow
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
import com.memind.mobile.core.search.ScoredItem as SearchScoredItem
import com.memind.mobile.core.search.TextSearch
import com.memind.mobile.core.search.VectorSearch
import com.memind.mobile.core.store.MemoryStore
import com.memind.mobile.core.store.InMemoryStore
import com.memind.mobile.core.store.MemoryItem
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
     * 使用完整检索请求执行混合检索。
     *
     * 默认路径是本地 BM25 文本检索；当注入 VectorSearch 且 embedding 可用时，再用 RRF 融合向量召回。
     */
    override suspend fun retrieve(request: RetrievalRequest): RetrievalResult {
        val id = request.memoryId
        val query = request.query
        val strategy = request.config.strategy
        val config = request.config
        if (query.isBlank()) return RetrievalResult.empty("$strategy", query)

        val candidateLimit = candidateLimit(config)
        val storeCandidates = store.getItems(
            memoryId = id,
            limit = candidateLimit,
            scope = request.scope,
            categories = request.categories,
        )
        if (storeCandidates.isNotEmpty()) {
            // 持久化 store 重新打开后，内存文本索引可能为空；检索前按候选集补索引。
            textSearch.index(id, storeCandidates)
        }
        val expandedQuery = expandedQuery(request)
        val keywordResults = textSearch.search(id, expandedQuery, limit = candidateLimit)
            .filterByRequest(request)
        val vectorResults = vectorResults(request, candidateLimit)
            .filterByRequest(request)
        val fused = fuseByRrf(keywordResults, vectorResults, config)
        val reranked = if (config.enableRerank || strategy == Strategy.DEEP) {
            rerankDeepLite(fused, request)
        } else {
            fused
        }
        val resultItems = reranked
            .filter { it.score >= config.minScore }
            .take(config.maxResults.coerceAtLeast(0))
        val result = RetrievalResult(
            items = resultItems.map { candidate ->
                val item = candidate.item
                RetrievalResult.ScoredItem(
                    id = item.id,
                    text = item.text,
                    score = candidate.score,
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
     * 构建上下文窗口。
     *
     * 阶段 5 先组合 recent messages 和已有检索结果；后续会扩展为 hybrid retrieval 与多层截断。
     */
    override suspend fun getContext(request: ContextRequest): ContextWindow {
        if (closed.get()) {
            return ContextWindow.bufferOnly(emptyList())
        }
        val recentMessages = recentBuffer.load(request.memoryId)
            .takeLast(request.recentMessageLimit.coerceAtLeast(0))
        val retrieval = if (request.includeMemories && !request.query.isNullOrBlank()) {
            retrieve(
                RetrievalRequest(
                    memoryId = request.memoryId,
                    query = request.query,
                    conversationHistory = recentMessages.map { it.content },
                    config = request.retrievalConfig.withStrategy(request.strategy),
                ),
            )
        } else {
            RetrievalResult.empty(request.strategy.name, request.query.orEmpty())
        }
        return fitContextWindow(
            recentMessages = recentMessages,
            memories = retrieval,
            maxTokens = request.maxTokens,
        )
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

    /**
     * 计算本次检索候选集上限。
     *
     * 至少覆盖 maxResults/topK 的若干倍，避免融合前过早截断，同时设置硬上限保护移动端。
     */
    private fun candidateLimit(config: RetrievalConfig): Int {
        val requested = maxOf(config.maxCandidates, config.maxResults * 8, config.topK * 8)
        return requested.coerceIn(config.maxResults.coerceAtLeast(1), 1_000)
    }

    /**
     * 构建本地扩展查询。
     *
     * Deep-lite 只拼接最近 conversationHistory，不调用 LLM，保证弱网环境可用。
     */
    private fun expandedQuery(request: RetrievalRequest): String {
        val config = request.config
        if (!config.enableQueryExpansion && config.strategy != Strategy.DEEP) return request.query
        val history = request.conversationHistory
            .takeLast(4)
            .joinToString(" ")
            .trim()
        return listOf(request.query, history)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    /**
     * 执行可选向量召回。
     *
     * 只有宿主注入 VectorSearch 且配置允许时才会调用 embedding；失败时返回空列表并降级为纯文本检索。
     */
    private suspend fun vectorResults(
        request: RetrievalRequest,
        candidateLimit: Int,
    ): List<SearchScoredItem> {
        val vectors = vectorSearch ?: return emptyList()
        if (!request.config.enableVectorSearch) return emptyList()
        val embedding = runCatching { chatClient.embed(request.query).embedding }.getOrDefault(emptyList())
        if (embedding.isEmpty()) return emptyList()
        val ids = vectors.search(
            memoryId = request.memoryId,
            queryEmbedding = embedding,
            limit = maxOf(request.config.topK, request.config.maxResults, candidateLimit / 2),
        )
        if (ids.isEmpty()) return emptyList()
        return store.getItemsByIds(request.memoryId, ids)
            .mapIndexed { index, item ->
                val rankScore = 1.0 - index.toDouble() / ids.size.coerceAtLeast(1).toDouble()
                SearchScoredItem(item, rankScore.coerceAtLeast(0.0))
            }
    }

    /**
     * 过滤检索请求限定条件。
     *
     * scope/category 统一在融合前执行，避免不符合条件的候选进入最终排序。
     */
    private fun List<SearchScoredItem>.filterByRequest(request: RetrievalRequest): List<SearchScoredItem> {
        val categoryFilter = request.categories.orEmpty()
        return filter { scored ->
            (request.scope == null || scored.item.scope == request.scope) &&
                (categoryFilter.isEmpty() || scored.item.category in categoryFilter)
        }
    }

    /**
     * 使用归一化 RRF 融合文本与向量候选。
     *
     * RRF 只依赖排序位置，能自然兼容 BM25 分数和向量召回顺序不同尺度的问题。
     */
    private fun fuseByRrf(
        keywordResults: List<SearchScoredItem>,
        vectorResults: List<SearchScoredItem>,
        config: RetrievalConfig,
    ): List<HybridCandidate> {
        val sources = listOf(keywordResults, vectorResults).filter { it.isNotEmpty() }
        if (sources.isEmpty()) return emptyList()
        val merged = linkedMapOf<String, HybridCandidate>()
        sources.forEach { source ->
            val maxRawScore = source.maxOfOrNull { it.score }?.takeIf { it > 0.0 } ?: 1.0
            source.forEachIndexed { index, scored ->
                val rank = index + 1
                val normalizedRrf = (config.rrfK.coerceAtLeast(1) + 1.0) /
                    (config.rrfK.coerceAtLeast(1) + rank.toDouble())
                val rawBonus = (scored.score / maxRawScore).coerceIn(0.0, 1.0) * 0.05
                val previous = merged[scored.item.id]
                val nextScore = (previous?.score ?: 0.0) + normalizedRrf + rawBonus
                merged[scored.item.id] = HybridCandidate(scored.item, nextScore)
            }
        }
        return merged.values
            .map { it.copy(score = it.score / sources.size.toDouble()) }
            .sortedWith(compareByDescending<HybridCandidate> { it.score }.thenByDescending { it.item.createdAt })
    }

    /**
     * 执行 Deep-lite 本地重排。
     *
     * 只使用查询、conversationHistory 和 item 文本的词项重叠，不强制调用 LLM reranker。
     */
    private fun rerankDeepLite(
        candidates: List<HybridCandidate>,
        request: RetrievalRequest,
    ): List<HybridCandidate> {
        val terms = retrievalTerms(expandedQuery(request)).toSet()
        if (terms.isEmpty()) return candidates
        return candidates
            .map { candidate ->
                val itemTerms = retrievalTerms(
                    listOf(
                        candidate.item.text,
                        candidate.item.category?.categoryName.orEmpty(),
                        candidate.item.metadata.values.joinToString(" "),
                    ).joinToString(" "),
                ).toSet()
                val overlap = if (itemTerms.isEmpty()) {
                    0.0
                } else {
                    terms.count { it in itemTerms }.toDouble() / terms.size.toDouble()
                }
                candidate.copy(score = candidate.score * 0.85 + overlap * 0.15)
            }
            .sortedWith(compareByDescending<HybridCandidate> { it.score }.thenByDescending { it.item.createdAt })
    }

    /**
     * 拆分重排词项。
     *
     * 该逻辑与 SimpleTextSearch 保持同一轻量分词原则，避免引入 tokenizer 依赖。
     */
    private fun retrievalTerms(text: String): List<String> {
        val terms = mutableListOf<String>()
        val current = StringBuilder()
        text.lowercase().forEach { char ->
            when {
                char.isLetterOrDigit() && !char.isCjk() -> current.append(char)
                char.isCjk() -> {
                    if (current.isNotEmpty()) {
                        terms.add(current.toString())
                        current.clear()
                    }
                    terms.add(char.toString())
                }
                else -> {
                    if (current.isNotEmpty()) {
                        terms.add(current.toString())
                        current.clear()
                    }
                }
            }
        }
        if (current.isNotEmpty()) {
            terms.add(current.toString())
        }
        return terms.filter { it.isNotBlank() }
    }

    /**
     * 判断字符是否属于常见 CJK 文字区间。
     *
     * 用于中文查询的单字级轻量召回和重排。
     */
    private fun Char.isCjk(): Boolean {
        val block = Character.UnicodeBlock.of(this)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
    }

    /**
     * 按轻量 token 预算裁剪上下文窗口。
     *
     * 移动端先使用字符数 / 4 的粗估算，优先保留最近消息，再保留最高排序的记忆。
     */
    private fun fitContextWindow(
        recentMessages: List<Message>,
        memories: RetrievalResult,
        maxTokens: Int,
    ): ContextWindow {
        val budget = maxTokens.coerceAtLeast(0)
        val keptMessages = mutableListOf<Message>()
        var usedTokens = 0

        recentMessages.asReversed().forEach { message ->
            val messageTokens = estimateTokens(message.content)
            if (usedTokens + messageTokens <= budget || keptMessages.isEmpty()) {
                keptMessages.add(message)
                usedTokens += messageTokens
            }
        }
        keptMessages.reverse()

        val keptItems = mutableListOf<RetrievalResult.ScoredItem>()
        memories.items.forEach { item ->
            val itemTokens = estimateTokens(item.text)
            if (usedTokens + itemTokens <= budget || keptItems.isEmpty() && keptMessages.isEmpty()) {
                keptItems.add(item)
                usedTokens += itemTokens
            }
        }
        val trimmedMemories = memories.copy(items = keptItems)
        return ContextWindow(
            recentMessages = keptMessages,
            memories = trimmedMemories,
            totalTokens = usedTokens,
        )
    }

    /**
     * 估算文本 token 数。
     *
     * 这里用保守的字符长度近似，避免核心模块依赖具体 tokenizer。
     */
    private fun estimateTokens(text: String): Int = (text.length + 3) / 4

    private data class HybridCandidate(
        val item: MemoryItem,
        val score: Double,
    )

}
