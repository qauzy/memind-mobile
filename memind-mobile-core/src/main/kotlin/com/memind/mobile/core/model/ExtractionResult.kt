package com.memind.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class ExtractionResult(
    val memoryId: MemoryId? = null,
    val status: ExtractionStatus = ExtractionStatus.SUCCESS,
    val rawDataId: String? = null,
    val itemIds: List<String> = emptyList(),
    val insightIds: List<String> = emptyList(),
    val durationMs: Long = 0,
    val errorMessage: String? = null,
    val totalMemoryItems: Int = 0,
    val totalInsights: Int = 0,
) {
    public val isSuccess: Boolean get() = status == ExtractionStatus.SUCCESS

    public val isFailed: Boolean get() = status == ExtractionStatus.FAILED

    public companion object {
        public fun success(memoryId: MemoryId, durationMs: Long = 0): ExtractionResult =
            ExtractionResult(memoryId = memoryId, status = ExtractionStatus.SUCCESS, durationMs = durationMs)

        public fun failed(memoryId: MemoryId, errorMessage: String): ExtractionResult =
            ExtractionResult(memoryId = memoryId, status = ExtractionStatus.FAILED, errorMessage = errorMessage)
    }
}

@Serializable
public enum class ExtractionStatus {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
}