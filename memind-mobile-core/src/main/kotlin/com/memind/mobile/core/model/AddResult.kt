package com.memind.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class AddResult(
    val status: AddStatus,
    val messageId: String? = null,
    val rawDataId: String? = null,
    val errorMessage: String? = null,
)

@Serializable
public enum class AddStatus {
    ACCEPTED,
    BUFFERED,
    EXTRACTED,
    FAILED,
}