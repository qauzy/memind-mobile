package com.memind.mobile.core.store

import com.memind.mobile.core.model.MemoryId

public data class MemoryItem(
    val id: String,
    val memoryId: MemoryId,
    val text: String,
    val category: String? = null,
    val source: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
) {
    public fun toText(): String = text

    public fun vectorId(): String = "${
        memoryId.toIdentifier()
    }::$id"
}