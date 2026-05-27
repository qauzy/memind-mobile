package com.memind.mobile.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
public data class RawData(
    val id: String = UUID.randomUUID().toString(),
    val memoryId: MemoryId,
    val content: String,
    val contentType: String = "text",
    val sourceClient: String? = null,
    val contentId: String? = null,
    val caption: String = "",
    val captionVectorId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val resourceId: String? = null,
    val mimeType: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val startTime: Long? = null,
    val endTime: Long? = null,
)
