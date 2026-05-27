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
        public fun empty(memoryId: MemoryId): InsightTree = InsightTree(memoryId)
    }
}