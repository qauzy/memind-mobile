package com.memind.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class Message(
    val role: String,
    val content: String,
    val timestamp: Long? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    public fun textContent(): String = content

    public companion object {
        public fun user(content: String): Message = Message("user", content)

        public fun assistant(content: String): Message = Message("assistant", content)

        public fun system(content: String): Message = Message("system", content)
    }
}