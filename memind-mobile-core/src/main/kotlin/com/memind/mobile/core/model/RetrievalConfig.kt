package com.memind.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class RetrievalConfig(
    val strategy: Strategy = Strategy.SIMPLE,
    val maxResults: Int = 10,
    val minScore: Double = 0.0,
    val enableRerank: Boolean = false,
    val topK: Int = 5,
) {
    public fun withStrategy(strategy: Strategy): RetrievalConfig = copy(strategy = strategy)

    public fun withMaxResults(maxResults: Int): RetrievalConfig = copy(maxResults = maxResults)

    public fun withMinScore(minScore: Double): RetrievalConfig = copy(minScore = minScore)

    public companion object {
        public fun simple(): RetrievalConfig = RetrievalConfig(Strategy.SIMPLE)

        public fun deep(): RetrievalConfig = RetrievalConfig(Strategy.DEEP, enableRerank = true)
    }
}