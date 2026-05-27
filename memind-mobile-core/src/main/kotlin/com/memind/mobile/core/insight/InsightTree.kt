package com.memind.mobile.core.insight

import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.store.InsightNode
import com.memind.mobile.core.store.InsightTier

public data class InsightTree(
    val memoryId: MemoryId,
    val nodes: List<InsightNode> = emptyList(),
    val builtAt: Long = System.currentTimeMillis(),
) {
    public val leaves: List<InsightNode> get() = nodes.filter { it.tier == InsightTier.LEAF }

    public val branches: List<InsightNode> get() = nodes.filter { it.tier == InsightTier.BRANCH }

    public val roots: List<InsightNode> get() = nodes.filter { it.tier == InsightTier.ROOT }

    /**
     * 将 Insight Tree 格式化为可读文本。
     *
     * 返回 leaf、branch、root 三层结构，方便调试或注入 prompt。
     */
    public fun toDisplayString(): String = buildString {
        appendLine("=== Insight Tree ===")
        appendLine("Memory: ${memoryId.toIdentifier()}")
        appendLine()
        if (leaves.isNotEmpty()) {
            appendLine("🍃 Leaves:")
            leaves.forEach { appendLine("  • ${it.text}") }
            appendLine()
        }
        if (branches.isNotEmpty()) {
            appendLine("🌿 Branches:")
            branches.forEach { appendLine("  • ${it.text}") }
            appendLine()
        }
        if (roots.isNotEmpty()) {
            appendLine("🌳 Roots:")
            roots.forEach { appendLine("  • ${it.text}") }
        }
        if (nodes.isEmpty()) appendLine("  (empty)")
    }

    public companion object {
        /**
         * 创建空 Insight Tree。
         *
         * memoryId 用于保留记忆空间归属，nodes 默认为空。
         */
        public fun empty(memoryId: MemoryId): InsightTree = InsightTree(memoryId)
    }
}
