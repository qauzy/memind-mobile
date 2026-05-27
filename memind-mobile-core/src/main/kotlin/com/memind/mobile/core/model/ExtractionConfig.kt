package com.memind.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class ExtractionConfig(
    val enableInsight: Boolean = true,
    val scope: MemoryScope = MemoryScope.USER,
    val enableForesight: Boolean = false,
    val timeoutMs: Long = 600_000,
    val language: String = "en",
) {
    public fun withoutInsight(): ExtractionConfig = copy(enableInsight = false)

    public fun withScope(scope: MemoryScope): ExtractionConfig = copy(scope = scope)

    public fun withLanguage(language: String): ExtractionConfig = copy(language = language)

    public companion object {
        public fun defaults(): ExtractionConfig = ExtractionConfig()
    }
}