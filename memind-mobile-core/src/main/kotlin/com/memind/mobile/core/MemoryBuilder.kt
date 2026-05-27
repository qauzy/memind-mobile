package com.memind.mobile.core

import com.memind.mobile.core.llm.ChatClient
import com.memind.mobile.core.search.TextSearch
import com.memind.mobile.core.search.VectorSearch
import com.memind.mobile.core.store.MemoryStore

public interface MemoryBuilder {
    /**
     * 配置聊天与 embedding 客户端。
     *
     * client 负责远程模型能力，返回当前 builder 以支持链式调用。
     */
    public fun chatClient(client: ChatClient): MemoryBuilder

    /**
     * 配置记忆存储实现。
     *
     * store 可以是内存、Room 或宿主 App 自定义实现，返回当前 builder。
     */
    public fun store(store: MemoryStore): MemoryBuilder

    /**
     * 配置文本检索实现。
     *
     * search 负责关键词召回，返回当前 builder。
     */
    public fun textSearch(search: TextSearch): MemoryBuilder

    /**
     * 配置向量检索实现。
     *
     * search 负责语义召回，返回当前 builder。
     */
    public fun vectorSearch(search: VectorSearch): MemoryBuilder

    /**
     * 构建 Memory 实例。
     *
     * 返回可供宿主 App 调用的记忆系统入口。
     */
    public fun build(): Memory
}
