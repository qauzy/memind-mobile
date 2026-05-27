package com.memind.mobile.core

import com.memind.mobile.core.insight.InsightBuilder
import com.memind.mobile.core.insight.InsightTree
import com.memind.mobile.core.llm.ChatClient
import com.memind.mobile.core.model.AddResult
import com.memind.mobile.core.model.AddStatus
import com.memind.mobile.core.model.ExtractionConfig
import com.memind.mobile.core.model.ExtractionResult
import com.memind.mobile.core.model.HealthStatus
import com.memind.mobile.core.model.MemoryCategory
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.Message
import com.memind.mobile.core.model.RawData
import com.memind.mobile.core.model.RetrievalConfig
import com.memind.mobile.core.model.RetrievalRequest
import com.memind.mobile.core.model.RetrievalResult
import com.memind.mobile.core.model.RetrievalStatus
import com.memind.mobile.core.model.Strategy
import com.memind.mobile.core.search.SimpleTextSearch
import com.memind.mobile.core.search.TextSearch
import com.memind.mobile.core.search.VectorSearch
import com.memind.mobile.core.model.MemoryScope
import com.memind.mobile.core.store.MemoryItem
import com.memind.mobile.core.store.MemoryStore
import com.memind.mobile.core.store.InMemoryStore
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

public class DefaultMemory(
    private val chatClient: ChatClient,
    private val store: MemoryStore = InMemoryStore(),
    private val textSearch: TextSearch = SimpleTextSearch(),
    private val vectorSearch: VectorSearch? = null,
    private val insightBuilder: InsightBuilder = InsightBuilder(store),
) : Memory {
    private val closed = AtomicBoolean(false)

    /**
     * 添加单条消息到当前记忆空间。
     *
     * 现阶段仍采用轻量路径：直接生成结构化 MemoryItem；后续阶段会切换为 pending buffer + commit。
     */
    override suspend fun addMessage(
        id: MemoryId,
        message: Message,
        config: ExtractionConfig,
    ): AddResult {
        if (closed.get()) return AddResult(AddStatus.FAILED, errorMessage = "closed")
        val now = System.currentTimeMillis()
        // 先补齐原版 MemoryItem 的核心元数据字段，方便后续抽取 pipeline 无缝替换当前轻量实现。
        val item = MemoryItem(
            id = "${id.toIdentifier()}:$now",
            memoryId = id,
            text = message.content,
            scope = config.scope,
            category = defaultCategory(config.scope),
            contentType = "message",
            sourceClient = message.metadata["sourceClient"],
            source = message.role,
            contentHash = sha256(message.content),
            observedAt = message.timestamp,
            metadata = message.metadata,
            timestamp = message.timestamp ?: now,
            createdAt = now,
        )
        textSearch.index(id, listOf(item))
        store.saveItem(item)
        return AddResult(AddStatus.ACCEPTED)
    }

    /**
     * 批量添加消息到当前记忆空间。
     *
     * 当前按顺序复用单条添加逻辑，确保每条消息都建立独立的元数据和搜索索引。
     */
    override suspend fun addMessages(
        id: MemoryId,
        messages: List<Message>,
        config: ExtractionConfig,
    ): AddResult {
        messages.forEach { addMessage(id, it, config) }
        return AddResult(AddStatus.ACCEPTED)
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
        val now = System.currentTimeMillis()
        val rawData = RawData(
            memoryId = id,
            content = content,
            contentType = "text",
            createdAt = now,
        )
        store.saveRawData(rawData)

        // RawData 保存原始输入，MemoryItem 保存可检索事实；两者用 rawDataId 串联。
        val item = MemoryItem(
            id = "${id.toIdentifier()}:$now",
            memoryId = id,
            text = content,
            scope = config.scope,
            category = defaultCategory(config.scope),
            contentType = rawData.contentType,
            rawDataId = rawData.id,
            contentHash = sha256(content),
            timestamp = now,
            createdAt = now,
        )
        textSearch.index(id, listOf(item))
        store.saveItem(item)
        return ExtractionResult.success(id).copy(
            rawDataId = rawData.id,
            itemIds = listOf(item.id),
            totalMemoryItems = 1,
        )
    }

    /**
     * 提交当前记忆空间的待处理消息。
     *
     * 阶段 2 仅保留接口语义；阶段 3 会实现 pending buffer drain 和真实抽取。
     */
    override suspend fun commit(id: MemoryId): ExtractionResult {
        return commit(id, ExtractionConfig.defaults())
    }

    /**
     * 使用指定抽取配置提交待处理消息。
     *
     * 当前返回空成功结果，避免提前引入尚未完成的 buffer pipeline。
     */
    override suspend fun commit(id: MemoryId, config: ExtractionConfig): ExtractionResult {
        return ExtractionResult.success(id)
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
     * 根据 scope 推断默认分类。
     *
     * 在真正 LLM 分类器接入前，USER 默认落到 event，AGENT 默认落到 directive。
     */
    private fun defaultCategory(scope: MemoryScope): MemoryCategory =
        if (scope == MemoryScope.AGENT) MemoryCategory.DIRECTIVE else MemoryCategory.EVENT

    /**
     * 计算文本内容哈希。
     *
     * 用于后续 hash 去重和跨存储一致性判断。
     */
    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
