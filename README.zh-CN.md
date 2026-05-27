# Memind Mobile

[English](README.md)

Memind Mobile 是一个面向 Android App 嵌入的轻量记忆系统核心库。它把原本偏服务端的 Memind 核心能力重新整理为移动端可直接调用的 Kotlin/Android Library，宿主 App 不需要引入 Spring、JDBC 或服务端运行时，就可以在本地保存、检索和组织用户与 Agent 的长期记忆。

当前仓库的核心交付物是 `memind-mobile-core`：一个无 UI 的 Android AAR 模块，提供统一的 `Memory` API、可替换的存储层、轻量文本检索、Room 本地持久化和 OpenAI 兼容模型客户端。

## 核心思想

Memind Mobile 的设计目标是让移动端 Agent 拥有“可落地、可裁剪、可替换”的长期记忆能力：

- **移动端优先**：核心库以 Android AAR 形式交付，使用 Kotlin coroutine、Room、OkHttp 和 kotlinx.serialization，避免引入服务端框架。
- **纯 API 嵌入**：宿主 App 通过 `Memory.builder()` 组装依赖，然后直接调用 `addMessage`、`extract`、`retrieve`、`getInsightTree` 等函数。
- **记忆空间隔离**：使用 `MemoryId(userId, agentId)` 隔离不同用户和不同 Agent 的记忆，适合多账号、多 Agent 或多角色场景。
- **分层数据模型**：同时保留原始输入 `RawData`、可检索事实 `MemoryItem` 和可视化/归纳用的 `InsightTree`，为后续语义检索和记忆整理留出扩展空间。
- **可替换组件**：`ChatClient`、`MemoryStore`、`TextSearch`、`VectorSearch` 都是接口，默认实现轻量可用，宿主 App 可以替换成自己的模型、数据库或检索实现。

## 项目结构

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
        │   ├── Memory.kt                 # 对外核心 API
        │   ├── MemoryBuilder.kt          # 依赖组装入口
        │   ├── DefaultMemory.kt          # 默认实现
        │   ├── llm/                      # ChatClient 与 OpenAI 兼容客户端
        │   ├── model/                    # MemoryId、Message、检索/抽取结果等模型
        │   ├── search/                   # 文本检索与向量检索接口
        │   ├── store/                    # 记忆存储接口与内存实现
        │   ├── store/room/               # Room/SQLite 持久化实现
        │   └── insight/                  # Insight Tree 构建与结构
        └── test/kotlin/com/memind/mobile/core/
            └── MemoryTest.kt
```

## 架构概览

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

核心调用链可以理解为：

1. 宿主 App 用 `MemoryId` 指定一个记忆空间。
2. `addMessage` 或 `extract` 写入原始内容，并生成可检索的 `MemoryItem`。
3. `MemoryStore` 负责保存数据，默认可用内存存储，也可以使用 `RoomStore` 持久化到 SQLite。
4. `TextSearch` 负责轻量关键词召回，后续可通过 `VectorSearch` 扩展语义召回。
5. `getInsightTree` 从已有记忆构建轻量 Insight Tree，供 UI 或调试面板展示。

## 当前能力

- Android Library 模块：`com.memind.mobile.core`
- Kotlin 2.1.0，JVM target 17
- Android Gradle Plugin 8.7.3
- compileSdk 36.1，minSdk 21
- 支持内存存储 `InMemoryStore`
- 支持 Room 本地持久化 `RoomStore`
- 支持 OpenAI 兼容接口 `OpenAiClient`
- 支持基础文本检索 `SimpleTextSearch`
- 支持 USER/AGENT scope 与记忆分类过滤
- 支持发布 release AAR 与本地 Maven 仓库产物

## 环境要求

- JDK 17
- Android SDK，并安装 compileSdk 36.1
- Gradle 9.3.1 或更高版本

当前仓库已加入与 PokeClaw 对齐的 Gradle Wrapper，本地构建优先使用 `./gradlew`。项目使用 Android Gradle Plugin 9.1.0、Kotlin 2.1.21 和 Gradle 9.3.1。

## 编译与测试

在仓库根目录执行：

```bash
./gradlew :memind-mobile-core:assembleRelease
```

运行单元测试：

```bash
./gradlew :memind-mobile-core:test
```

清理并重新构建：

```bash
./gradlew :memind-mobile-core:clean :memind-mobile-core:assembleRelease
```

生成 release AAR 后，产物位于：

```text
memind-mobile-core/build/outputs/aar/memind-mobile-core-release.aar
```

## 发布到本地 Maven 仓库

项目已配置 `maven-publish`，可以把 `memind-mobile-core` 发布到模块构建目录下的本地 Maven 仓库：

```bash
./gradlew :memind-mobile-core:publishReleasePublicationToLocalBuildRepository
```

发布后的坐标为：

```text
com.memind.mobile:memind-mobile-core:0.1.0
```

仓库路径为：

```text
memind-mobile-core/build/repo
```

宿主项目可以这样引入：

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

## 基础用法

### 1. 创建 Memory 实例

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

如果你不希望核心库直接访问远程模型，可以实现自己的 `ChatClient`：

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

### 2. 写入消息并检索

```kotlin
import com.memind.mobile.core.model.MemoryId
import com.memind.mobile.core.model.Message
import com.memind.mobile.core.model.Strategy

val memoryId = MemoryId.of(userId = "user-001", agentId = "assistant")

memory.addMessage(
    id = memoryId,
    message = Message.user("我喜欢周末去山里徒步。"),
)

val result = memory.retrieve(
    id = memoryId,
    query = "徒步",
    strategy = Strategy.SIMPLE,
)

result.items.forEach { item ->
    println("${item.score}: ${item.text}")
}
```

### 3. 保存独立文本

```kotlin
val extraction = memory.extract(
    id = memoryId,
    content = "用户正在把 Kotlin 项目迁移到移动端。",
)

println(extraction.itemIds)
```

### 4. 使用 Room 持久化

```kotlin
import com.memind.mobile.core.Memory
import com.memind.mobile.core.store.room.RoomStore

val memory = Memory.builder()
    .chatClient(AppChatClient())
    .store(RoomStore.create(context))
    .build()
```

`RoomStore` 默认数据库名为 `memind-mobile.db`，数据保存在宿主 App 私有目录中。

### 5. 按 scope 或分类检索

```kotlin
import com.memind.mobile.core.model.MemoryCategory
import com.memind.mobile.core.model.RetrievalRequest

val userMemories = memory.retrieve(
    RetrievalRequest.userMemory(memoryId, "徒步"),
)

val eventMemories = memory.retrieve(
    RetrievalRequest.byCategories(
        memoryId = memoryId,
        query = "迁移",
        categories = setOf(MemoryCategory.EVENT),
    ),
)
```

## 设计边界

当前版本是移动端核心库的早期实现，重点是稳定 API、轻量本地存储和基础检索。以下能力已经预留接口，但仍在演进中：

- `commit` 当前保持接口语义，后续会接入 pending buffer 和批量抽取 pipeline。
- `VectorSearch` 已可注入，默认检索路径目前仍以文本检索为主。
- `InsightTree` 当前按需轻量构建，后续会演进为增量 dirty flag 与后台刷新机制。
- `OpenAiClient` 是 OpenAI 兼容接口适配器，生产环境建议由宿主 App 自行管理 API Key、代理、重试、日志脱敏和网络策略。

## 开发命令速查

```bash
# 查看可用任务
./gradlew tasks

# 编译 release AAR
./gradlew :memind-mobile-core:assembleRelease

# 运行测试
./gradlew :memind-mobile-core:test

# 发布到模块本地 Maven 仓库
./gradlew :memind-mobile-core:publishReleasePublicationToLocalBuildRepository
```

## License

当前仓库尚未声明开源许可证。正式对外开源前，请先补充 `LICENSE` 文件，并在本节标明许可证类型。
