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
    val isEmpty: Boolean get() = items.isEmpty(),

    @Serializable
    public data class ScoredItem(
        val id: String,
        val text: String,
        val score: Double,
        val category: String? = null,
        val source: String? = null,
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
        public fun empty(strategy: String, query: String): RetrievalResult =
            RetrievalResult(emptyList(), emptyList(), emptyList(), strategy, query, RetrievalStatus.EMPTY)
    }
}

@Serializable
public enum class RetrievalStatus {
    SUCCESS,
    EMPTY,
    DEGRADED,
}