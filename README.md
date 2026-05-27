# Memind Mobile

[简体中文](README.zh-CN.md)

Memind Mobile is a lightweight, embeddable memory-system library for agents and apps. It reorganizes the server-oriented Memind core into small Kotlin/JVM modules that can be used from Android, desktop JVM, command-line tools, or tests without requiring Spring, JDBC, an Android SDK, or a server runtime.

The default build produces two Android-free JAR modules:

- `memind-mobile-core`: the core `Memory` API, models, extraction pipeline, retrieval interfaces, in-memory store, and OpenAI-compatible client.
- `memind-store-json`: a simple JSONL persistence module that implements `MemoryStore` for local files.

Optional persistence modules are kept out of the default build path. `memind-store-sqlite` can be compiled with an explicit Gradle property, and Room/Android persistence can be reintroduced later as a separate optional Android module.

## Core Ideas

- **Small core first**: the core memory engine is a Kotlin/JVM JAR, so it can run without Android-specific tooling.
- **Pure API embedding**: host apps assemble dependencies through `Memory.builder()` and call functions such as `addMessage`, `extract`, `commit`, `retrieve`, and `getInsightTree`.
- **Isolated memory spaces**: `MemoryId(userId, agentId)` separates memories across users, agents, and roles.
- **Layered memory model**: the library keeps original inputs as `RawData`, searchable facts as `MemoryItem`, and summarized structures as `InsightTree`.
- **Replaceable components**: `ChatClient`, `MemoryStore`, `TextSearch`, and `VectorSearch` are interfaces. Apps can swap in their own model client, storage backend, or retrieval layer.
- **Offline-friendly defaults**: rule-based extraction and in-memory or JSONL storage work without a network model service.

## Project Structure

```text
memind-mobile/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md
├── README.zh-CN.md
├── memind-mobile-core/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/com/memind/mobile/core/
│       │   ├── Memory.kt                 # Public core API
│       │   ├── MemoryBuilder.kt          # Dependency assembly entry point
│       │   ├── DefaultMemory.kt          # Default implementation
│       │   ├── extract/                  # Rule/LLM extraction and dedup
│       │   ├── llm/                      # ChatClient and OpenAI-compatible client
│       │   ├── model/                    # MemoryId, Message, retrieval/extraction models
│       │   ├── search/                   # Text and vector retrieval interfaces
│       │   ├── store/                    # Store interface and in-memory implementation
│       │   └── insight/                  # Insight Tree structures and builder
│       └── test/kotlin/com/memind/mobile/core/
└── memind-store-json/
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/com/memind/mobile/store/json/
        │   └── JsonFileStore.kt          # JSONL MemoryStore implementation
        └── test/kotlin/com/memind/mobile/store/json/
└── memind-store-sqlite/
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/com/memind/mobile/store/sqlite/
        │   └── SqliteStore.kt            # Optional JVM SQLite MemoryStore
        └── test/kotlin/com/memind/mobile/store/sqlite/
```

## Architecture

```text
Host App or Agent
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
  - JsonFileStore          - VectorSearch interface
  - SqliteStore
  - custom store
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
2. `addMessage` writes messages into pending/recent buffers.
3. `commit` drains pending messages into `RawData` and extracted `MemoryItem` records.
4. `MemoryStore` persists data through `InMemoryStore`, `JsonFileStore`, or a custom implementation.
5. `retrieve` performs lightweight local recall today, with `VectorSearch` available as an extension point.
6. `getInsightTree` builds a lightweight tree from existing memory items for UI, inspection, or debugging.

## Features

- Kotlin/JVM core library, JVM target 17
- Gradle Wrapper 9.3.1, Kotlin 2.1.21, daemon JVM 21
- Default build requires no Android SDK
- In-memory storage with `InMemoryStore`
- JSONL file persistence with `JsonFileStore`
- Optional SQLite file persistence with `SqliteStore`
- OpenAI-compatible client with `OpenAiClient`
- Rule-based extraction fallback plus optional LLM JSON extraction
- Exact hash deduplication and optional semantic deduplication hook
- Basic text retrieval with `SimpleTextSearch`
- USER/AGENT scope filtering and memory-category filtering
- Local Maven publication support for both default modules

## Requirements

- JDK 21 for the Gradle daemon and compilation toolchain
- No Android SDK is required for the default build

The generated bytecode targets JVM 17. Use the repository Gradle Wrapper:

```bash
./gradlew --version
```

## Build and Test

Build the default modules from the repository root:

```bash
./gradlew build
```

Run module tests:

```bash
./gradlew :memind-mobile-core:test :memind-store-json:test
```

Build JARs only:

```bash
./gradlew :memind-mobile-core:jar :memind-store-json:jar
```

Generated JARs are written to:

```text
memind-mobile-core/build/libs/
memind-store-json/build/libs/
```

Build or test the optional SQLite store:

```bash
./gradlew -Pmemind.includeSqlite=true :memind-store-sqlite:test
./gradlew -Pmemind.includeSqlite=true :memind-store-sqlite:jar
```

## Publish to a Local Maven Repository

Both modules use `maven-publish` and publish to their own module-local build repository:

```bash
./gradlew :memind-mobile-core:publishReleasePublicationToLocalBuildRepository \
          :memind-store-json:publishReleasePublicationToLocalBuildRepository
```

Published coordinates:

```text
com.memind.mobile:memind-mobile-core:0.1.0
com.memind.mobile:memind-store-json:0.1.0
```

Publish the optional SQLite module with:

```bash
./gradlew -Pmemind.includeSqlite=true :memind-store-sqlite:publishReleasePublicationToLocalBuildRepository
```

SQLite coordinates:

```text
com.memind.mobile:memind-store-sqlite:0.1.0
```

Repository paths:

```text
memind-mobile-core/build/repo
memind-store-json/build/repo
```

A host project can consume them like this:

```kotlin
repositories {
    maven { url = uri("/path/to/memind-mobile/memind-mobile-core/build/repo") }
    maven { url = uri("/path/to/memind-mobile/memind-store-json/build/repo") }
    mavenCentral()
}

dependencies {
    implementation("com.memind.mobile:memind-mobile-core:0.1.0")
    implementation("com.memind.mobile:memind-store-json:0.1.0")
}
```

## Basic Usage

### 1. Create a Memory Instance

```kotlin
import com.memind.mobile.core.Memory
import com.memind.mobile.core.llm.OpenAiClient
import com.memind.mobile.store.json.JsonFileStore
import java.nio.file.Paths

val memory = Memory.builder()
    .chatClient(
        OpenAiClient(
            apiKey = "<YOUR_API_KEY>",
            baseUrl = "https://api.openai.com",
        ),
    )
    .store(JsonFileStore(Paths.get("memind-data")))
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

memory.commit(memoryId)

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
    content = "The user is migrating a Kotlin project to a portable JVM core.",
)

println(extraction.itemIds)
```

## Design Boundaries

This is an early portable-core implementation. The current focus is stable APIs, Android-free default builds, lightweight local storage, and basic retrieval.

- `JsonFileStore` is intended for light local persistence and tests. Larger datasets should use a dedicated SQLite module later.
- `SqliteStore` is optional and not included in the default build. Enable it with `-Pmemind.includeSqlite=true`.
- Room persistence is not part of the default build. A future optional Android module can provide it when an Android SDK path is available.
- `VectorSearch` can be injected, while the default retrieval path currently uses text search.
- `InsightTree` is built on demand for now and is expected to evolve toward dirty-flag based incremental refresh.
- `OpenAiClient` is an OpenAI-compatible adapter. In production, host apps should manage API keys, proxy settings, retries, log redaction, and network policy.

## Developer Commands

```bash
# List available tasks
./gradlew tasks

# Build the default Android-free modules
./gradlew build

# Run tests
./gradlew :memind-mobile-core:test :memind-store-json:test

# Build JARs
./gradlew :memind-mobile-core:jar :memind-store-json:jar

# Test optional SQLite store
./gradlew -Pmemind.includeSqlite=true :memind-store-sqlite:test
```

## License

This repository does not declare a license yet. Before publishing it as open source, add a `LICENSE` file and update this section with the chosen license.
