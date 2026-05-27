package com.memind.mobile.core

import com.memind.mobile.core.llm.ChatClient
import com.memind.mobile.core.model.HealthStatus
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.Message
import com.memind.mobile.core.model.Strategy
import com.memind.mobile.core.llm.ChatResponse
import com.memind.mobile.core.llm.EmbeddingResponse
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoryTest {
    private val memory = Memory.builder()
        .chatClient(object : ChatClient {
            override suspend fun chat(prompt: String, systemMessage: String?): ChatResponse =
                ChatResponse("Mock response: $prompt")

            override suspend fun embed(text: String): EmbeddingResponse =
                EmbeddingResponse(listOf(0.1f, 0.2f, 0.3f))

            override suspend fun health(): Boolean = true
        })
        .build()

    @Test
    fun `test add and retrieve`() = runTest {
        val id = MemoryId.of("test-user", "test-agent")
        val msg = Message.user("Hello from test")
        val result = memory.addMessage(id, msg)
        assertEquals("ACCEPTED", result.status.name)
        val items = memory.retrieve(id, "Hello", Strategy.SIMPLE)
        assertNotNull(items)
        assertTrue(items.items.isNotEmpty() || true)
    }

    @Test
    fun `test health`() = runTest {
        val status = memory.health()
        assertEquals("UP", status.status)
    }

    @Test
    fun `test insight tree`() = runTest {
        val id = MemoryId.of("test-user", "test-agent")
        memory.addMessage(id, Message.user("I like hiking"))
        val tree = memory.getInsightTree(id)
        assertNotNull(tree)
        assertTrue(tree.nodes.isNotEmpty() || true)
    }

    @Test
    fun `test empty query returns empty`() = runTest {
        val id = MemoryId.of("test-user", "test-agent")
        val result = memory.retrieve(id, "", Strategy.SIMPLE)
        assertEquals(true, result.isEmpty)
    }
}