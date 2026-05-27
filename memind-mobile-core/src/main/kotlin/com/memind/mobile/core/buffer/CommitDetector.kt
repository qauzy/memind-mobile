package com.memind.mobile.core.buffer

import com.memind.mobile.core.model.Message

public interface CommitDetector {
    /**
     * 判断当前 pending 消息是否应该自动提交。
     *
     * pendingMessages 是当前记忆空间未提交消息，latestMessage 是刚写入的最新消息。
     */
    public fun shouldCommit(
        pendingMessages: List<Message>,
        latestMessage: Message,
    ): Boolean
}

public class LocalRuleCommitDetector(
    private val maxPendingMessages: Int = 12,
) : CommitDetector {
    /**
     * 使用本地规则判断提交边界。
     *
     * 默认只在显式 metadata 标记或 pending 条数达到阈值时触发，避免移动端频繁抽取。
     */
    override fun shouldCommit(
        pendingMessages: List<Message>,
        latestMessage: Message,
    ): Boolean {
        val explicitCommit = latestMessage.metadata["commit"] == "true" ||
            latestMessage.metadata["memindCommit"] == "true"
        return explicitCommit || pendingMessages.size >= maxPendingMessages
    }
}
