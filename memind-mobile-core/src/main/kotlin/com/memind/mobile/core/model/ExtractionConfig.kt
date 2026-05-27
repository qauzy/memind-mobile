package com.memind.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class ExtractionConfig(
    val enableInsight: Boolean = true,
    val scope: MemoryScope = MemoryScope.USER,
    val enableForesight: Boolean = false,
    val timeoutMs: Long = 600_000,
    val language: String = "en",
) {
    /**
     * 返回关闭 Insight 生成后的抽取配置。
     *
     * 用于移动端低功耗或只需要写入记忆 item 的场景。
     */
    public fun withoutInsight(): ExtractionConfig = copy(enableInsight = false)

    /**
     * 返回替换记忆 scope 后的抽取配置。
     *
     * scope 决定抽取结果进入 USER 记忆还是 AGENT 记忆。
     */
    public fun withScope(scope: MemoryScope): ExtractionConfig = copy(scope = scope)

    /**
     * 返回替换语言设置后的抽取配置。
     *
     * language 供后续 LLM 抽取 prompt 选择语言。
     */
    public fun withLanguage(language: String): ExtractionConfig = copy(language = language)

    public companion object {
        /**
         * 创建默认抽取配置。
         *
         * 返回面向移动端的安全默认值。
         */
        public fun defaults(): ExtractionConfig = ExtractionConfig()
    }
}
