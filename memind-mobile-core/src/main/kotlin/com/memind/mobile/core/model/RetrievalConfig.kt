package com.memind.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class RetrievalConfig(
    val strategy: Strategy = Strategy.SIMPLE,
    val maxResults: Int = 10,
    val minScore: Double = 0.0,
    val enableRerank: Boolean = false,
    val topK: Int = 5,
    val maxCandidates: Int = 200,
    val rrfK: Int = 60,
    val enableVectorSearch: Boolean = true,
    val enableQueryExpansion: Boolean = false,
) {
    /**
     * 返回替换检索策略后的配置。
     *
     * strategy 决定 simple/deep 等检索路径，其余配置保持不变。
     */
    public fun withStrategy(strategy: Strategy): RetrievalConfig = copy(strategy = strategy)

    /**
     * 返回替换最大结果数后的配置。
     *
     * maxResults 用于控制移动端检索返回量和后续排序成本。
     */
    public fun withMaxResults(maxResults: Int): RetrievalConfig = copy(maxResults = maxResults)

    /**
     * 返回替换最低分阈值后的配置。
     *
     * minScore 用于过滤低相关性结果。
     */
    public fun withMinScore(minScore: Double): RetrievalConfig = copy(minScore = minScore)

    /**
     * 返回替换候选集上限后的配置。
     *
     * maxCandidates 控制本地检索和融合前最多加载多少条记忆，避免移动端全量扫描失控。
     */
    public fun withMaxCandidates(maxCandidates: Int): RetrievalConfig = copy(maxCandidates = maxCandidates)

    /**
     * 返回替换向量检索开关后的配置。
     *
     * enableVectorSearch 为 false 时，即使注入 VectorSearch 也只走文本检索。
     */
    public fun withVectorSearch(enabled: Boolean): RetrievalConfig = copy(enableVectorSearch = enabled)

    /**
     * 返回替换轻量查询扩展开关后的配置。
     *
     * 查询扩展只使用本地 conversationHistory，不强制调用 LLM。
     */
    public fun withQueryExpansion(enabled: Boolean): RetrievalConfig = copy(enableQueryExpansion = enabled)

    public companion object {
        /**
         * 创建轻量检索配置。
         *
         * 默认使用 SIMPLE 策略，适合移动端实时路径。
         */
        public fun simple(): RetrievalConfig = RetrievalConfig(Strategy.SIMPLE)

        /**
         * 创建深度检索配置。
         *
         * 默认开启 rerank 标记，供后续混合检索阶段接入。
         */
        public fun deep(): RetrievalConfig =
            RetrievalConfig(
                strategy = Strategy.DEEP,
                enableRerank = true,
                enableQueryExpansion = true,
                maxCandidates = 300,
            )
    }
}
