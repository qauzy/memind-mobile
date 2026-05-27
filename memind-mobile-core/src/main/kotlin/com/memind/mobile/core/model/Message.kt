package com.memind.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class Message(
    val role: String,
    val content: String,
    val timestamp: Long? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    /**
     * 返回消息正文文本。
     *
     * 该函数为检索和抽取层提供统一的文本读取入口。
     */
    public fun textContent(): String = content

    public companion object {
        /**
         * 创建用户消息。
         *
         * content 是用户输入文本，返回 role 为 user 的 Message。
         */
        public fun user(content: String): Message = Message("user", content)

        /**
         * 创建助手消息。
         *
         * content 是助手回复文本，返回 role 为 assistant 的 Message。
         */
        public fun assistant(content: String): Message = Message("assistant", content)

        /**
         * 创建系统消息。
         *
         * content 是系统提示文本，返回 role 为 system 的 Message。
         */
        public fun system(content: String): Message = Message("system", content)
    }
}
