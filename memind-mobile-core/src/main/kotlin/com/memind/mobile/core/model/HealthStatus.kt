package com.memind.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class HealthStatus(
    val status: String,
    val service: String = "memind-mobile",
    val version: String = "0.1.0",
    val message: String? = null,
) {
    public val isUp: Boolean get() = status == "UP"
}