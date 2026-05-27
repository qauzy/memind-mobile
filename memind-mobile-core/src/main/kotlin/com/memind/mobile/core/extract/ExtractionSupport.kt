package com.memind.mobile.core.extract

import com.memind.mobile.core.model.MemoryCategory
import com.memind.mobile.core.model.MemoryScope
import java.security.MessageDigest

internal object ExtractionSupport {
    /**
     * 根据 scope 推断默认分类。
     *
     * 在真正分类器接入前，USER 默认落到 event，AGENT 默认落到 directive。
     */
    fun defaultCategory(scope: MemoryScope): MemoryCategory =
        if (scope == MemoryScope.AGENT) MemoryCategory.DIRECTIVE else MemoryCategory.EVENT

    /**
     * 计算文本内容哈希。
     *
     * 用于精确去重和跨存储一致性判断。
     */
    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
