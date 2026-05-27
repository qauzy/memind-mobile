package com.memind.mobile.core.model

public data class MemoryId(
    val userId: String,
    val agentId: String,
) {
    public fun toIdentifier(): String = "$userId:$agentId"

    public companion object {
        public fun of(userId: String, agentId: String): MemoryId = MemoryId(userId, agentId)

        public fun of(userId: String): MemoryId = MemoryId(userId, "default")

        public fun parse(identifier: String): MemoryId {
            val parts = identifier.split(":", limit = 2)
            return MemoryId(parts[0], if (parts.size > 1) parts[1] else "default")
        }
    }
}