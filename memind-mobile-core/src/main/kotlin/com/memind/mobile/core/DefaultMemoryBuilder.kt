package com.memind.mobile.core

import com.memind.mobile.core.llm.ChatClient
import com.memind.mobile.core.store.MemoryStore
import com.memind.mobile.core.search.TextSearch
import com.memind.mobile.core.search.VectorSearch

public class DefaultMemoryBuilder : MemoryBuilder {
    private var chatClient: ChatClient? = null
    private var store: MemoryStore? = null
    private var textSearch: TextSearch? = null
    private var vectorSearch: VectorSearch? = null

    override fun chatClient(client: ChatClient): MemoryBuilder {
        this.chatClient = client
        return this
    }

    override fun store(store: MemoryStore): MemoryBuilder {
        this.store = store
        return this
    }

    override fun textSearch(search: TextSearch): MemoryBuilder {
        this.textSearch = search
        return this
    }

    override fun vectorSearch(search: VectorSearch): MemoryBuilder {
        this.vectorSearch = search
        return this
    }

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