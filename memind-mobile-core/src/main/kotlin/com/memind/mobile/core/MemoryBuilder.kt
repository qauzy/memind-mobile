package com.memind.mobile.core

import com.memind.mobile.core.llm.ChatClient
import com.memind.mobile.core.search.TextSearch
import com.memind.mobile.core.search.VectorSearch
import com.memind.mobile.core.store.MemoryStore

public interface MemoryBuilder {
    public fun chatClient(client: ChatClient): MemoryBuilder

    public fun store(store: MemoryStore): MemoryBuilder

    public fun textSearch(search: TextSearch): MemoryBuilder

    public fun vectorSearch(search: VectorSearch): MemoryBuilder

    public fun build(): Memory
}