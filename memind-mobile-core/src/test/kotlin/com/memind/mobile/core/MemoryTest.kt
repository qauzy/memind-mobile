package com.memind.mobile.core

import com.memind.mobile.core.llm.ChatClient
import com.memind.mobile.core.llm.ChatResponse
import com.memind.mobile.core.llm.EmbeddingResponse
import com.memind.mobile.core.model.ExtractionConfig
import com.memind.mobile.core.model.MemoryCategory
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.MemoryScope
import com.memind.mobile.core.model.Message
import com.memind.mobile.core.model.RetrievalRequest
import com.memind.mobile.core.model.Strategy
import com.memind.mobile.core.search.InMemoryVectorSearch
import com.memind.mobile.core.store.InMemoryStore
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoryTest {
    private val memory = Memory.builder()
        .chatClient(object : ChatClient {
            /**
             * 返回测试用聊天响应。
             *
             * 避免单元测试依赖真实网络模型。
             */
            override suspend fun chat(prompt: String, systemMessage: String?): ChatResponse =
                ChatResponse("Mock response: $prompt")

            /**
             * 返回测试用 embedding。
             *
             * 固定向量便于测试稳定运行。
             */
            override suspend fun embed(text: String): EmbeddingResponse =
                EmbeddingResponse(listOf(0.1f, 0.2f, 0.3f))

            /**
             * 返回测试用健康状态。
             *
             * 保证 health 测试不受外部服务影响。
             */
            override suspend fun health(): Boolean = true
        })
        .build()

    /**
     * 验证添加消息后可以通过关键词检索到记忆。
     *
     * 同时确认默认 USER 记忆会落到 event 分类。
     */
    @Test
    fun `test add and retrieve`() = runTest {
        val id = MemoryId.of("test-user", "test-agent")
        val msg = Message.user("Hello from test")
        val result = memory.addMessage(id, msg)
        assertEquals("BUFFERED", result.status.name)
        memory.commit(id)
        val items = memory.retrieve(id, "Hello", Strategy.SIMPLE)
        assertNotNull(items)
        assertTrue(items.items.isNotEmpty())
        assertEquals("event", items.items.first().category)
    }

    /**
     * 验证健康检查返回 UP。
     *
     * 使用 mock ChatClient 保证测试不访问网络。
     */
    @Test
    fun `test health`() = runTest {
        val status = memory.health()
        assertEquals("UP", status.status)
    }

    /**
     * 验证 Insight Tree 可以基于已有 item 构建。
     *
     * 当前是轻量树，后续会替换为增量 insight layer。
     */
    @Test
    fun `test insight tree`() = runTest {
        val id = MemoryId.of("test-user", "test-agent")
        memory.addMessage(id, Message.user("I like hiking"))
        memory.commit(id)
        val tree = memory.getInsightTree(id)
        assertNotNull(tree)
        assertTrue(tree.nodes.isNotEmpty())
    }

    /**
     * 验证空查询返回空检索结果。
     *
     * 该行为对应原版 retrieval admission 的最小保护。
     */
    @Test
    fun `test empty query returns empty`() = runTest {
        val id = MemoryId.of("test-user", "test-agent")
        val result = memory.retrieve(id, "", Strategy.SIMPLE)
        assertEquals(true, result.isEmpty)
    }

    /**
     * 验证独立文本抽取会保存 RawData。
     *
     * RawData 是后续三层检索和原文回溯的基础。
     */
    @Test
    fun `test extract stores raw data`() = runTest {
        val store = InMemoryStore()
        val scopedMemory = Memory.builder()
            .chatClient(mockChatClient())
            .store(store)
            .build()
        val id = MemoryId.of("raw-user", "raw-agent")

        val result = scopedMemory.extract(id, "The user is migrating a Kotlin app.")

        assertEquals(true, result.isSuccess)
        val rawDataId = assertNotNull(result.rawDataId)
        assertNotNull(store.getRawData(id, rawDataId))
        assertEquals(1, store.count(id))
    }

    /**
     * 验证 RetrievalRequest 的 scope/category 过滤。
     *
     * 该测试保护 USER/AGENT 双 scope 和七类记忆分类的调用面。
     */
    @Test
    fun `test retrieval request filters by scope and category`() = runTest {
        val id = MemoryId.of("scope-user", "scope-agent")
        memory.addMessage(id, Message.user("User likes hiking trails"))
        memory.commit(id)
        memory.addMessage(
            id,
            Message.assistant("Always use the stable sync playbook"),
            ExtractionConfig.defaults().withScope(MemoryScope.AGENT),
        )
        memory.commit(id, ExtractionConfig.defaults().withScope(MemoryScope.AGENT))

        val userResult = memory.retrieve(RetrievalRequest.userMemory(id, "hiking"))
        val agentResult = memory.retrieve(RetrievalRequest.agentMemory(id, "playbook"))
        val eventResult = memory.retrieve(
            RetrievalRequest.byCategories(id, "hiking", setOf(MemoryCategory.EVENT)),
        )

        assertTrue(userResult.items.isNotEmpty())
        assertTrue(agentResult.items.isNotEmpty())
        assertTrue(eventResult.items.isNotEmpty())
        assertTrue(userResult.items.all { it.scope == MemoryScope.USER })
        assertTrue(agentResult.items.all { it.scope == MemoryScope.AGENT })
        assertTrue(eventResult.items.all { it.category == "event" })
    }

    /**
     * 验证 addMessage 只写入缓冲区。
     *
     * commit 前不应检索到新消息，commit 后才会生成 MemoryItem。
     */
    @Test
    fun `test add message buffers until commit`() = runTest {
        val id = MemoryId.of("buffer-user", "buffer-agent")

        memory.addMessage(id, Message.user("Buffered memory should wait"))
        val beforeCommit = memory.retrieve(id, "Buffered", Strategy.SIMPLE)
        memory.commit(id)
        val afterCommit = memory.retrieve(id, "Buffered", Strategy.SIMPLE)

        assertTrue(beforeCommit.items.isEmpty())
        assertTrue(afterCommit.items.isNotEmpty())
    }

    /**
     * 验证显式 metadata 可以触发自动提交。
     *
     * 该测试保护阶段 3 的轻量本地边界检测路径。
     */
    @Test
    fun `test metadata can trigger automatic commit`() = runTest {
        val scopedMemory = Memory.builder()
            .chatClient(mockChatClient())
            .build()
        val id = MemoryId.of("auto-user", "auto-agent")

        val result = scopedMemory.addMessage(
            id,
            Message("user", "Commit this message automatically", metadata = mapOf("commit" to "true")),
        )
        val retrieved = scopedMemory.retrieve(id, "automatically", Strategy.SIMPLE)

        assertEquals("EXTRACTED", result.status.name)
        assertTrue(retrieved.items.isNotEmpty())
    }

    /**
     * 验证规则抽取器会执行 hash 去重。
     *
     * 相同内容重复抽取时不应创建第二条 MemoryItem。
     */
    @Test
    fun `test rule extractor deduplicates exact content`() = runTest {
        val store = InMemoryStore()
        val scopedMemory = Memory.builder()
            .chatClient(mockChatClient())
            .store(store)
            .build()
        val id = MemoryId.of("dedup-user", "dedup-agent")

        scopedMemory.extract(id, "The user prefers compact summaries.")
        scopedMemory.extract(id, "The user prefers compact summaries.")

        assertEquals(1, store.count(id))
    }

    /**
     * 验证 LLM JSON 抽取路径。
     *
     * 只有显式启用 withLlmExtraction 时才调用模型，否则默认规则抽取。
     */
    @Test
    fun `test llm json extractor when enabled`() = runTest {
        val scopedMemory = Memory.builder()
            .chatClient(object : ChatClient {
                override suspend fun chat(prompt: String, systemMessage: String?): ChatResponse =
                    ChatResponse("""{"items":[{"text":"User prefers short answers.","category":"behavior","type":"fact"}]}""")

                override suspend fun embed(text: String): EmbeddingResponse =
                    EmbeddingResponse(listOf(0.1f, 0.2f))

                override suspend fun health(): Boolean = true
            })
            .build()
        val id = MemoryId.of("llm-user", "llm-agent")

        val result = scopedMemory.extract(
            id,
            "The user prefers short answers.",
            ExtractionConfig.defaults().withLlmExtraction(),
        )
        val retrieved = scopedMemory.retrieve(id, "short answers", Strategy.SIMPLE)

        assertEquals(true, result.isSuccess)
        assertEquals("behavior", retrieved.items.first().category)
    }

    /**
     * 验证轻量时间解析。
     *
     * 文本中的 yyyy-MM-dd 会写入 occurredAt，便于后续 temporal retrieval。
     */
    @Test
    fun `test extractor parses explicit date`() = runTest {
        val store = InMemoryStore()
        val scopedMemory = Memory.builder()
            .chatClient(mockChatClient())
            .store(store)
            .build()
        val id = MemoryId.of("time-user", "time-agent")

        scopedMemory.extract(id, "On 2026-05-27 the user finished migration.")
        val item = store.getItems(id).first()

        assertNotNull(item.occurredAt)
        assertEquals("day", item.timeGranularity)
    }

    /**
     * 验证语义去重可复用向量索引。
     *
     * 测试使用固定 embedding，让不同文本命中同一个最近向量。
     */
    @Test
    fun `test semantic dedup uses vector search when enabled`() = runTest {
        val store = InMemoryStore()
        val scopedMemory = Memory.builder()
            .chatClient(mockChatClient())
            .store(store)
            .vectorSearch(InMemoryVectorSearch())
            .build()
        val id = MemoryId.of("semantic-user", "semantic-agent")
        val config = ExtractionConfig.defaults().withSemanticDedup()

        scopedMemory.extract(id, "The user likes concise summaries.", config)
        scopedMemory.extract(id, "The user prefers brief summaries.", config)

        assertEquals(1, store.count(id))
    }

    /**
     * 创建测试用 ChatClient。
     *
     * 提供固定聊天、embedding 和健康检查结果，避免测试访问外部环境。
     */
    private fun mockChatClient(): ChatClient =
        object : ChatClient {
            /**
             * 返回测试用聊天响应。
             *
             * prompt 会被回显，便于断言调用链。
             */
            override suspend fun chat(prompt: String, systemMessage: String?): ChatResponse =
                ChatResponse("Mock response: $prompt")

            /**
             * 返回测试用 embedding。
             *
             * 使用固定三维向量保持测试稳定。
             */
            override suspend fun embed(text: String): EmbeddingResponse =
                EmbeddingResponse(listOf(0.1f, 0.2f, 0.3f))

            /**
             * 返回测试用健康状态。
             *
             * 始终为 true，避免外部依赖影响单元测试。
             */
            override suspend fun health(): Boolean = true
        }
}
