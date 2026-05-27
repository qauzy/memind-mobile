package com.memind.mobile.core.insight

import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.store.InsightNode
import com.memind.mobile.core.store.InsightTier
import com.memind.mobile.core.store.MemoryItem
import com.memind.mobile.core.store.MemoryStore

public class InsightBuilder(
    private val store: MemoryStore,
) {
    public suspend fun buildTree(memoryId: MemoryId): InsightTree {
        val items = store.getItems(memoryId, limit = 100)
        val existingInsights = store.getInsights(memoryId)

        if (items.isEmpty()) {
            return InsightTree.empty(memoryId)
        }

        // Step 1: Group items by category → build LEAF nodes
        val leaves = buildLeaves(items)

        // Step 2: Find cross-category patterns → build BRANCH nodes
        val branches = buildBranches(memoryId, leaves)

        // Step 3: Find cross-dimensional patterns → build ROOT nodes
        val roots = buildRoots(memoryId, branches)

        // Combine all nodes
        val allNodes = (leaves + branches + roots).toList()

        return InsightTree(memoryId, allNodes)
    }

    private suspend fun buildLeaves(items: List<MemoryItem>): List<InsightNode> {
        return items
            .groupBy { it.category ?: "general" }
            .flatMap { (category, groupItems) ->
                groupItems
                    .filter { it.text.isNotBlank() }
                    .map { item ->
                        InsightNode(
                            text = item.text,
                            tier = InsightTier.LEAF,
                            memoryId = item.memoryId,
                        )
                    }
            }
            .take(50)
    }

    private suspend fun buildBranches(
        memoryId: MemoryId,
        leaves: List<InsightNode>,
    ): List<InsightNode> {
        val categories = leaves.map { it.text }
            .flatMap { text ->
                text.split(",").map { it.trim() }.filter { it.isNotBlank() }
            }
            .distinct()
            .take(10)

        if (categories.size < 2) return emptyList()

        // Group related leaves into branch summaries
        return categories.map { category ->
            val relevantLeaves = leaves.filter { it.text.contains(category, ignoreCase = true) }
            if (relevantLeaves.size < 2) return@map null

            InsightNode(
                text = "Cross-pattern insight: $category appears across ${relevantLeaves.size} items",
                tier = InsightTier.BRANCH,
                memoryId = memoryId,
                parentId = null,
            )
        }.filterNotNull()
    }

    private suspend fun buildRoots(
        memoryId: MemoryId,
        branches: List<InsightNode>,
    ): List<InsightNode> {
        if (branches.size < 2) return emptyList()

        val branchTexts = branches.map { it.text }.joinToString("\n")
        return listOf(
            InsightNode(
                text = "Cross-dimensional insight from ${branches.size} branches:\n$branchTexts",
                tier = InsightTier.ROOT,
                memoryId = memoryId,
            ),
        )
    }
}