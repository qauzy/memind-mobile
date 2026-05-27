# Memind Mobile

[简体中文](README.zh-CN.md)

Memind Mobile is a lightweight memory-system core library designed for embedded Android apps. It reorganizes the server-oriented Memind core into a Kotlin/Android library that can be called directly from a mobile app, without bringing in Spring, JDBC, or a server runtime.

The main deliverable in this repository is `memind-mobile-core`: a UI-free Android AAR module that provides a unified `Memory` API, replaceable storage, lightweight text retrieval, Room persistence, and an OpenAI-compatible model client.

## Core Ideas

Memind Mobile is built around a simple goal: give mobile agents a long-term memory layer that is practical, small enough to embed, and easy to replace piece by piece.

- **Mobile first**: the core ships as an Android AAR and uses Kotlin coroutines, Room, OkHttp, and kotlinx.serialization instead of server-side frameworks.
- **Pure API embedding**: host apps assemble dependencies through `Memory.builder()` and call functions such as `addMessage`, `extract`, `retrieve`, and `getInsightTree`.
- **Isolated memory spaces**: `MemoryId(userId, agentId)` separates memories across users and agents, which fits multi-account, multi-agent, and role-specific scenarios.
- **Layered memory model**: the library keeps original inputs as `RawData`, searchable facts as `MemoryItem`, and summarized structures as `InsightTree`.
- **Replaceable components**: `ChatClient`, `MemoryStore`, `TextSearch`, and `VectorSearch` are interfaces. The default implementations are lightweight, while host apps can inject their own model, database, or retrieval layer.

## Project Structure

```text
memind-mobile/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── AGENT.md
├── TASK_PLAN.md
├── README.md
├── README.zh-CN.md
└── memind-mobile-core/
    ├── build.gradle.kts
    ├── consumer-rules.pro
    └── src/
        ├── main/kotlin/com/memind/mobile/core/
        │   ├── Memory.kt                 # Public core API
        │   ├── MemoryBuilder.kt          # Dependency assembly entry point
        │   ├── DefaultMemory.kt          # Default implementation
        │   ├── llm/                      # ChatClient and OpenAI-compatible client
        │   ├── model/                    # MemoryId, Message, retrieval/extraction models
        │   ├── search/                   # Text and vector retrieval interfaces
        │   ├── store/                    # Store interface and in-memory implementation
        │   ├── store/room/               # Room/SQLite persistence
        │   └── insight/                  # Insight Tree structures and builder
        └── test/kotlin/com/memind/mobile/core/
            └── MemoryTest.kt
```

## Architecture

```text
Host Android App
        |
        v
Memory API
  - addMessage / addMessages
  - extract / commit
  - retrieve
  - getInsightTree
  - health
        |
        +--------------------+
        | DefaultMemory      |
        +--------------------+
        |                    |
        v                    v
MemoryStore              Search
  - InMemoryStore          - SimpleTextSearch
  - RoomStore              - VectorSearch interface
        |
        v
RawData / MemoryItem / InsightNode
        |
        v
InsightTree

ChatClient
  - OpenAiClient
  - custom client from host app
```

The core flow is:

1. The host app selects a memory space with `MemoryId`.
2. `addMessage` or `extract` writes the source content and creates searchable `MemoryItem` records.
3. `MemoryStore` persists data through either `InMemoryStore`, `RoomStore`, or a custom implementation.
4. `TextSearch` handles lightweight keyword recall. `VectorSearch` is available as an extension point for semantic retrieval.
5. `getInsightTree` builds a lightweight tree from existing memory items for UI, inspection, or debugging.

## Features

- Android Library module: `com.memind.mobile.core`
- Kotlin 2.1.0, JVM target 17
- Android Gradle Plugin 8.7.3
- compileSdk 36.1, minSdk 21
- In-memory storage with `InMemoryStore`
- Local Room persistence with `RoomStore`
- OpenAI-compatible client with `OpenAiClient`
- Basic text retrieval with `SimpleTextSearch`
- USER/AGENT scope filtering and memory-category filtering
- Release AAR and local Maven publication support

## Requirements

- JDK 17
- Android SDK with compileSdk 36.1 installed
- Gradle 9.3.1 or newer

This repository includes a Gradle Wrapper aligned with PokeClaw. Prefer `./gradlew` for local builds. The project uses Android Gradle Plugin 9.1.0, Kotlin 2.1.21, and Gradle 9.3.1.

## Build and Test

Build the release AAR from the repository root:

```bash
./gradlew :memind-mobile-core:assembleRelease
```

Run unit tests:

```bash
./gradlew :memind-mobile-core:test
```

Clean and rebuild:

```bash
./gradlew :memind-mobile-core:clean :memind-mobile-core:assembleRelease
```

The generated release AAR is written to:

```text
memind-mobile-core/build/outputs/aar/memind-mobile-core-release.aar
```

## Publish to a Local Maven Repository

The project is configured with `maven-publish`. Publish `memind-mobile-core` to the module-local Maven repository with:

```bash
./gradlew :memind-mobile-core:publishReleasePublicationToLocalBuildRepository
```

Published coordinates:

```text
com.memind.mobile:memind-mobile-core:0.1.0
```

Repository path:

```text
memind-mobile-core/build/repo
```

A host project can consume it like this:

```kotlin
repositories {
    maven {
        url = uri("/path/to/memind-mobile/memind-mobile-core/build/repo")
    }
    google()
    mavenCentral()
}

dependencies {
    implementation("com.memind.mobile:memind-mobile-core:0.1.0")
}
```

## Basic Usage

### 1. Create a Memory Instance

```kotlin
import com.memind.mobile.core.Memory
import com.memind.mobile.core.llm.OpenAiClient

val memory = Memory.builder()
    .chatClient(
        OpenAiClient(
            apiKey = "<YOUR_API_KEY>",
            baseUrl = "https://api.openai.com",
        ),
    )
    .build()
```

If the core library should not call a remote model directly, implement your own `ChatClient`:

```kotlin
import com.memind.mobile.core.llm.ChatClient
import com.memind.mobile.core.llm.ChatResponse
import com.memind.mobile.core.llm.EmbeddingResponse

class AppChatClient : ChatClient {
    override suspend fun chat(prompt: String, systemMessage: String?): ChatResponse {
        return ChatResponse(content = "ok")
    }

    override suspend fun embed(text: String): EmbeddingResponse {
        return EmbeddingResponse(embedding = emptyList())
    }

    override suspend fun health(): Boolean = true
}
```

### 2. Add and Retrieve Memories

```kotlin
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.Message
import com.memind.mobile.core.model.Strategy

val memoryId = MemoryId.of(userId = "user-001", agentId = "assistant")

memory.addMessage(
    id = memoryId,
    message = Message.user("I like hiking in the mountains on weekends."),
)

val result = memory.retrieve(
    id = memoryId,
    query = "hiking",
    strategy = Strategy.SIMPLE,
)

result.items.forEach { item ->
    println("${item.score}: ${item.text}")
}
```

### 3. Store Standalone Text

```kotlin
val extraction = memory.extract(
    id = memoryId,
    content = "The user is migrating a Kotlin project to mobile.",
)

println(extraction.itemIds)
```

### 4. Use Room Persistence

```kotlin
import com.memind.mobile.core.Memory
import com.memind.mobile.core.store.room.RoomStore

val memory = Memory.builder()
    .chatClient(AppChatClient())
    .store(RoomStore.create(context))
    .build()
```

`RoomStore` uses `memind-mobile.db` by default and stores data in the host app's private storage.

### 5. Retrieve by Scope or Category

```kotlin
import com.memind.mobile.core.model.MemoryCategory
import com.memind.mobile.core.model.RetrievalRequest

val userMemories = memory.retrieve(
    RetrievalRequest.userMemory(memoryId, "hiking"),
)

val eventMemories = memory.retrieve(
    RetrievalRequest.byCategories(
        memoryId = memoryId,
        query = "migration",
        categories = setOf(MemoryCategory.EVENT),
    ),
)
```

## Design Boundaries

This is an early mobile-core implementation. The current focus is stable APIs, lightweight local storage, and basic retrieval. Several extension points are already present but still evolving:

- `commit` currently preserves the API contract and will later connect to a pending-buffer and batch-extraction pipeline.
- `VectorSearch` can be injected, while the default retrieval path currently uses text search.
- `InsightTree` is built on demand for now and is expected to evolve toward dirty-flag based incremental refresh.
- `OpenAiClient` is an OpenAI-compatible adapter. In production, host apps should manage API keys, proxy settings, retries, log redaction, and network policy.

## Developer Commands

```bash
# List available tasks
./gradlew tasks

# Build release AAR
./gradlew :memind-mobile-core:assembleRelease

# Run tests
./gradlew :memind-mobile-core:test

# Publish to the module-local Maven repository
./gradlew :memind-mobile-core:publishReleasePublicationToLocalBuildRepository
```

## License

This repository does not declare a license yet. Before publishing it as open source, add a `LICENSE` file and update this section with the chosen license.
