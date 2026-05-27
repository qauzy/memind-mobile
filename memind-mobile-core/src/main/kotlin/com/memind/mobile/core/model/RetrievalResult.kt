package com.memind.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class RetrievalResult(
    val items: List<ScoredItem> = emptyList(),
    val insights: List<InsightEntry> = emptyList(),
    val rawData: List<RawDataEntry> = emptyList(),
    val strategy: String = "SIMPLE",
    val query: String = "",
    val status: RetrievalStatus = RetrievalStatus.EMPTY,
) {
    public val isEmpty: Boolean
        get() = items.isEmpty() && insights.isEmpty() && rawData.isEmpty()

    @Serializable
    public data class ScoredItem(
        val id: String,
        val text: String,
        val score: Double,
        val category: String? = null,
        val source: String? = null,
        val scope: MemoryScope? = null,
        val rawDataId: String? = null,
        val occurredAt: Long? = null,
        val metadata: Map<String, String> = emptyMap(),
    )

    @Serializable
    public data class InsightEntry(
        val id: String,
        val text: String,
        val tier: String? = null,
    )

    @Serializable
    public data class RawDataEntry(
        val id: String,
        val caption: String = "",
        val maxScore: Double = 0.0,
    )

    public companion object {
        /**
         * 创建空检索结果。
         *
         * 用于空查询、无召回或降级路径中的正常空返回。
         */
        public fun empty(strategy: String, query: String): RetrievalResult =
            RetrievalResult(emptyList(), emptyList(), emptyList(), strategy, query, RetrievalStatus.EMPTY)
    }

    /**
     * 将检索结果格式化为 prompt 友好的文本。
     *
     * 按 insights、items、captions 三层输出，贴近原版 Memind 的上下文组织方式。
     */
    public fun formattedResult(): String {
        val insightSection = insights
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "## Insights\n", separator = "\n") { "- ${it.text}" }
            .orEmpty()
        val itemSection = items
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "## Items\n", separator = "\n") { "- ${it.text}" }
            .orEmpty()
        val rawSection = rawData
            .filter { it.caption.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "## Captions\n", separator = "\n") { "- ${it.caption}" }
            .orEmpty()

        return listOf(insightSection, itemSection, rawSection)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }
}

@Serializable
public enum class RetrievalStatus {
    SUCCESS,
    EMPTY,
    DEGRADED,
}
