package com.memind.mobile.core

import com.memind.mobile.core.insight.InsightTree
import com.memind.mobile.core.model.AddResult
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
import com.memind.mobile.core.model.Strategy

/**
 * Memind-Mobile 核心 API。
 *
 * 宿主 App 通过 [Memory.builder] 注入模型客户端、存储和检索组件，
 * 再调用 add/extract/retrieve/insight 等函数获得记忆能力。
 */
public interface Memory : AutoCloseable {
    // ===== Builder =====
    public companion object {
        /**
         * 创建 Memory 构建器。
         *
         * 宿主 App 通过该入口注入 ChatClient、Store 和检索组件。
         */
        public fun builder(): MemoryBuilder = DefaultMemoryBuilder()
    }

    // ===== Extraction =====
    /**
     * 添加单条消息。
     *
     * 当前接口语义面向实时对话；后续实现会先进入缓冲区再由 commit 抽取。
     */
    public suspend fun addMessage(
        id: MemoryId,
        message: Message,
        config: ExtractionConfig = ExtractionConfig.defaults(),
    ): AddResult

    /**
     * 批量添加消息。
     *
     * 用于宿主 App 已经整理好一段对话时一次性写入。
     */
    public suspend fun addMessages(
        id: MemoryId,
        messages: List<Message>,
        config: ExtractionConfig = ExtractionConfig.defaults(),
    ): AddResult

    /**
     * 从独立文本抽取记忆。
     *
     * 适合笔记、摘要、文档片段等非实时对话输入。
     */
    public suspend fun extract(
        id: MemoryId,
        content: String,
        config: ExtractionConfig = ExtractionConfig.defaults(),
    ): ExtractionResult

    // ===== Commit =====
    /**
     * 提交当前记忆空间的待处理消息。
     *
     * 返回抽取结果，空缓冲区时应返回空成功结果。
     */
    public suspend fun commit(id: MemoryId): ExtractionResult

    /**
     * 使用指定抽取配置提交待处理消息。
     *
     * 允许宿主 App 为本次提交指定 scope、语言和 insight 开关。
     */
    public suspend fun commit(id: MemoryId, config: ExtractionConfig): ExtractionResult

    // ===== Retrieval =====
    /**
     * 使用简化参数检索记忆。
     *
     * 该函数保留易用入口，内部实现可转成 RetrievalRequest。
     */
    public suspend fun retrieve(
        id: MemoryId,
        query: String,
        strategy: Strategy = Strategy.SIMPLE,
        config: RetrievalConfig = RetrievalConfig.simple(),
    ): RetrievalResult

    /**
     * 使用完整检索请求检索记忆。
     *
     * 支持 scope、category、历史上下文和检索配置等高级控制。
     */
    public suspend fun retrieve(request: RetrievalRequest): RetrievalResult

    // ===== Context =====
    /**
     * 构建可直接注入模型 prompt 的上下文窗口。
     *
     * 默认组合 recent buffer 和可选记忆检索结果，供宿主 App 在回复前取上下文。
     */
    public suspend fun getContext(request: ContextRequest): ContextWindow

    // ===== Insight =====
    /**
     * 获取当前记忆空间的 Insight Tree。
     *
     * 返回 leaf、branch、root 分层结构。
     */
    public suspend fun getInsightTree(id: MemoryId): InsightTree

    /**
     * 刷新 Insight 构建。
     *
     * 用于宿主 App 在会话结束或后台任务中主动触发 insight 处理。
     */
    public suspend fun flushInsights(id: MemoryId)

    // ===== Health =====
    /**
     * 获取运行时健康状态。
     *
     * 主要用于判断远程 LLM/Embedding 增强能力是否可用。
     */
    public suspend fun health(): HealthStatus

    // ===== Lifecycle =====
    /**
     * 释放 Memory 实例资源。
     *
     * 宿主 App 生命周期结束时应调用该函数。
     */
    override fun close()
}
