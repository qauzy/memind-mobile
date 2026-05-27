package com.memind.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class ContextRequest(
    val memoryId: MemoryId,
    val query: String? = null,
    val strategy: Strategy = Strategy.SIMPLE,
    val retrievalConfig: RetrievalConfig = RetrievalConfig.simple(),
    val recentMessageLimit: Int = 20,
    val maxTokens: Int = 4_000,
    val includeMemories: Boolean = true,
)
