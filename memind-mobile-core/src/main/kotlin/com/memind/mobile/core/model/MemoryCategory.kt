package com.memind.mobile.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class MemoryCategory(
    public val categoryName: String,
    public val scope: MemoryScope,
) {
    @SerialName("profile")
    PROFILE("profile", MemoryScope.USER),

    @SerialName("behavior")
    BEHAVIOR("behavior", MemoryScope.USER),

    @SerialName("event")
    EVENT("event", MemoryScope.USER),

    @SerialName("tool")
    TOOL("tool", MemoryScope.AGENT),

    @SerialName("directive")
    DIRECTIVE("directive", MemoryScope.AGENT),

    @SerialName("playbook")
    PLAYBOOK("playbook", MemoryScope.AGENT),

    @SerialName("resolution")
    RESOLUTION("resolution", MemoryScope.AGENT);

    public companion object {
        /**
         * 根据分类名解析 MemoryCategory。
         *
         * 同时兼容枚举名和原版 Memind 的小写 categoryName。
         */
        public fun fromName(name: String?): MemoryCategory? {
            if (name.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.categoryName.equals(name, ignoreCase = true) ||
                    it.name.equals(name, ignoreCase = true)
            }
        }

        /**
         * 返回 USER scope 支持的分类集合。
         *
         * 对应原版 profile、behavior、event 三类用户记忆。
         */
        public fun userCategories(): Set<MemoryCategory> =
            setOf(PROFILE, BEHAVIOR, EVENT)

        /**
         * 返回 AGENT scope 支持的分类集合。
         *
         * 对应原版 tool、directive、playbook、resolution 四类代理记忆。
         */
        public fun agentCategories(): Set<MemoryCategory> =
            setOf(TOOL, DIRECTIVE, PLAYBOOK, RESOLUTION)
    }
}
