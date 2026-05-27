package com.memind.mobile.core.insight

import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.store.InsightNode
import com.memind.mobile.core.store.InsightTier
import com.memind.mobile.core.store.MemoryItem
import com.memind.mobile.core.store.MemoryStore

public class InsightBuilder(
    private val store: MemoryStore,
) {
    /**
     * 构建当前记忆空间的 Insight Tree。
     *
     * memoryId 限定数据范围，返回 leaf、branch、root 三层轻量洞察结构。
     */
    public suspend fun buildTree(memoryId: MemoryId): InsightTree {
        val items = store.getItems(memoryId, limit = 100)

        if (items.isEmpty()) {
            return InsightTree.empty(memoryId)
        }

        // 第一步按记忆分类聚合 item，先生成最细粒度的 LEAF 节点。
        val leaves = buildLeaves(items)

        // 第二步从多个 LEAF 中寻找重复主题，生成跨 item 的 BRANCH 节点。
        val branches = buildBranches(memoryId, leaves)

        // 第三步把多个 BRANCH 汇总为 ROOT，保留原版分层 Insight 的核心形态。
        val roots = buildRoots(memoryId, branches)

        // 将三层节点合并为一个树视图，调用方仍可按 tier 重新筛选。
        val allNodes = (leaves + branches + roots).toList()

        return InsightTree(memoryId, allNodes)
    }

    /**
     * 从记忆 item 构建叶子节点。
     *
     * items 是已限制数量的结构化记忆，返回最多 50 条 LEAF 节点。
     */
    private suspend fun buildLeaves(items: List<MemoryItem>): List<InsightNode> {
        return items
            .groupBy { it.category?.categoryName ?: "general" }
            .flatMap { (_, groupItems) ->
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

    /**
     * 从叶子节点构建分支节点。
     *
     * leaves 是候选叶子洞察，返回跨多条记忆重复出现的轻量主题。
     */
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

        // 相同主题至少命中两条 LEAF 才形成 BRANCH，避免单条记忆制造噪声洞察。
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

    /**
     * 从分支节点构建根节点。
     *
     * branches 是跨 item 主题，返回跨主题汇总后的 ROOT 节点。
     */
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
