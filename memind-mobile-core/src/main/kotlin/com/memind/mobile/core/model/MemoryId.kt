package com.memind.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class MemoryId(
    val userId: String,
    val agentId: String,
) {
    /**
     * 将用户和代理标识压缩成稳定字符串。
     *
     * 该值用于 store、search 和 Room 索引的 memoryKey。
     */
    public fun toIdentifier(): String = "$userId:$agentId"

    public companion object {
        /**
         * 使用 userId 和 agentId 创建 MemoryId。
         *
         * 适合多 Agent 场景下显式隔离记忆空间。
         */
        public fun of(userId: String, agentId: String): MemoryId = MemoryId(userId, agentId)

        /**
         * 使用默认 agentId 创建 MemoryId。
         *
         * 适合单 Agent 或简单 App 内嵌场景。
         */
        public fun of(userId: String): MemoryId = MemoryId(userId, "default")

        /**
         * 从字符串解析 MemoryId。
         *
         * 若没有 agentId 部分，则自动使用 default。
         */
        public fun parse(identifier: String): MemoryId {
            val parts = identifier.split(":", limit = 2)
            return MemoryId(parts[0], if (parts.size > 1) parts[1] else "default")
        }
    }
}
