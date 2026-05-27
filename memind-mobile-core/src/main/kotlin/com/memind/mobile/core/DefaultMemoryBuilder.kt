package com.memind.mobile.core

import com.memind.mobile.core.llm.ChatClient
import com.memind.mobile.core.search.SimpleTextSearch
import com.memind.mobile.core.store.MemoryStore
import com.memind.mobile.core.search.TextSearch
import com.memind.mobile.core.search.VectorSearch
import com.memind.mobile.core.store.InMemoryStore

public class DefaultMemoryBuilder : MemoryBuilder {
    private var chatClient: ChatClient? = null
    private var store: MemoryStore? = null
    private var textSearch: TextSearch? = null
    private var vectorSearch: VectorSearch? = null

    /**
     * 设置用于聊天、抽取和 embedding 的客户端。
     *
     * 返回 builder 自身，方便宿主 App 链式配置。
     */
    override fun chatClient(client: ChatClient): MemoryBuilder {
        this.chatClient = client
        return this
    }

    /**
     * 设置记忆存储实现。
     *
     * 可注入 InMemoryStore、RoomStore 或宿主自定义的持久化实现。
     */
    override fun store(store: MemoryStore): MemoryBuilder {
        this.store = store
        return this
    }

    /**
     * 设置文本检索实现。
     *
     * 阶段 2 保持轻量文本检索入口，后续 hybrid retrieval 会在此基础上扩展。
     */
    override fun textSearch(search: TextSearch): MemoryBuilder {
        this.textSearch = search
        return this
    }

    /**
     * 设置向量检索实现。
     *
     * 当前仅完成依赖注入，真正参与检索会在后续混合检索阶段接入。
     */
    override fun vectorSearch(search: VectorSearch): MemoryBuilder {
        this.vectorSearch = search
        return this
    }

    /**
     * 构建 Memory 实例。
     *
     * chatClient 是必需项，其余组件使用移动端轻量默认实现。
     */
    override fun build(): Memory {
        require(chatClient != null) { "chatClient is required" }
        return DefaultMemory(
            chatClient = chatClient!!,
            store = store ?: InMemoryStore(),
            textSearch = textSearch ?: SimpleTextSearch(),
            vectorSearch = vectorSearch,
        )
    }
}
