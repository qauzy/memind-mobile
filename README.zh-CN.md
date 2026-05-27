# Memind Mobile

[English](README.md)

Memind Mobile 是一个适合嵌入 Agent 和 App 的轻量记忆系统库。它把偏服务端的 Memind 核心能力整理成小型 Kotlin/JVM 模块，可以用于 Android、桌面 JVM、命令行工具或测试环境，不要求 Spring、JDBC、Android SDK 或服务端运行时。

默认构建只产出两个不依赖 Android 的 JAR 模块：

- `memind-mobile-core`：核心 `Memory` API、数据模型、抽取 pipeline、检索接口、内存存储和 OpenAI 兼容客户端。
- `memind-store-json`：基于 JSONL 文件的简单持久化模块，实现 `MemoryStore`。

可选持久化模块不在默认构建路径里。`memind-store-sqlite` 可以通过显式 Gradle 属性编译；Room/Android 持久化后续也可以作为独立可选 Android 模块重新接入。

## 核心思想

- **小核心优先**：核心记忆引擎是 Kotlin/JVM JAR，可以脱离 Android 工具链运行。
- **纯 API 嵌入**：宿主 App 通过 `Memory.builder()` 组装依赖，然后直接调用 `addMessage`、`extract`、`commit`、`retrieve`、`getInsightTree` 等函数。
- **记忆空间隔离**：使用 `MemoryId(userId, agentId)` 隔离不同用户、Agent 和角色。
- **分层数据模型**：同时保留原始输入 `RawData`、可检索事实 `MemoryItem` 和归纳结构 `InsightTree`。
- **可替换组件**：`ChatClient`、`MemoryStore`、`TextSearch`、`VectorSearch` 都是接口，宿主可以替换成自己的模型、数据库或检索实现。
- **默认离线可用**：规则抽取、内存存储和 JSONL 持久化都不依赖远程模型服务。

## 项目结构

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
│       │   ├── Memory.kt                 # 对外核心 API
│       │   ├── MemoryBuilder.kt          # 依赖组装入口
│       │   ├── DefaultMemory.kt          # 默认实现
│       │   ├── extract/                  # 规则/LLM 抽取与去重
│       │   ├── llm/                      # ChatClient 与 OpenAI 兼容客户端
│       │   ├── model/                    # MemoryId、Message、检索/抽取模型
│       │   ├── search/                   # 文本检索与向量检索接口
│       │   ├── store/                    # 存储接口与内存实现
│       │   └── insight/                  # Insight Tree 结构与构建
│       └── test/kotlin/com/memind/mobile/core/
└── memind-store-json/
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/com/memind/mobile/store/json/
        │   └── JsonFileStore.kt          # JSONL MemoryStore 实现
        └── test/kotlin/com/memind/mobile/store/json/
└── memind-store-sqlite/
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/com/memind/mobile/store/sqlite/
        │   └── SqliteStore.kt            # 可选 JVM SQLite MemoryStore
        └── test/kotlin/com/memind/mobile/store/sqlite/
```

## 架构概览

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

核心调用链可以理解为：

1. 宿主用 `MemoryId` 指定一个记忆空间。
2. `addMessage` 把消息写入 pending/recent buffer。
3. `commit` 把 pending 消息整理为 `RawData` 和抽取后的 `MemoryItem`。
4. `MemoryStore` 通过 `InMemoryStore`、`JsonFileStore` 或自定义实现持久化。
5. `retrieve` 当前执行 BM25 风格本地召回；配置 `VectorSearch` 后可用 RRF 融合已存向量结果。
6. `getInsightTree` 从已有记忆构建轻量树结构，供 UI、调试或检查使用。

## 当前能力

- Kotlin/JVM 核心库，JVM target 17
- Gradle Wrapper 9.3.1、Kotlin 2.1.21、Daemon JVM 21
- 默认构建不需要 Android SDK
- 支持内存存储 `InMemoryStore`
- 支持 JSONL 文件持久化 `JsonFileStore`
- 支持可选 SQLite 文件持久化 `SqliteStore`
- 支持 OpenAI 兼容接口 `OpenAiClient`
- 支持规则抽取 fallback 和可选 LLM JSON 抽取
- 支持精确 hash 去重和可选语义去重扩展点
- 支持 BM25 风格文本检索、可选向量融合和 RRF
- 支持 Deep-lite 本地历史扩展与轻量重排
- 支持 USER/AGENT scope 与记忆分类过滤
- 支持两个默认模块发布到本地 Maven 仓库

## 环境要求

- JDK 21，用于 Gradle daemon 和编译 toolchain
- 默认构建不需要 Android SDK

生成的字节码目标是 JVM 17。建议始终使用仓库内 Gradle Wrapper：

```bash
./gradlew --version
```

## 编译与测试

在仓库根目录构建默认模块：

```bash
./gradlew build
```

运行模块测试：

```bash
./gradlew :memind-mobile-core:test :memind-store-json:test
```

只构建 JAR：

```bash
./gradlew :memind-mobile-core:jar :memind-store-json:jar
```

生成的 JAR 位于：

```text
memind-mobile-core/build/libs/
memind-store-json/build/libs/
```

编译或测试可选 SQLite store：

```bash
./gradlew -Pmemind.includeSqlite=true :memind-store-sqlite:test
./gradlew -Pmemind.includeSqlite=true :memind-store-sqlite:jar
```

## 发布到本地 Maven 仓库

两个模块都配置了 `maven-publish`，会发布到各自模块的 build 仓库：

```bash
./gradlew :memind-mobile-core:publishReleasePublicationToLocalBuildRepository \
          :memind-store-json:publishReleasePublicationToLocalBuildRepository
```

发布坐标：

```text
com.memind.mobile:memind-mobile-core:0.1.0
com.memind.mobile:memind-store-json:0.1.0
```

发布可选 SQLite 模块：

```bash
./gradlew -Pmemind.includeSqlite=true :memind-store-sqlite:publishReleasePublicationToLocalBuildRepository
```

SQLite 坐标：

```text
com.memind.mobile:memind-store-sqlite:0.1.0
```

仓库路径：

```text
memind-mobile-core/build/repo
memind-store-json/build/repo
```

宿主项目可以这样引入：

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

## 基础用法

### 1. 创建 Memory 实例

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

如果不希望核心库直接访问远程模型，可以实现自己的 `ChatClient`：

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

memory.commit(memoryId)

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
    content = "用户正在把 Kotlin 项目迁移到可移植 JVM 核心。",
)

println(extraction.itemIds)
```

### 4. 构建上下文窗口

```kotlin
import com.memind.mobile.core.model.ContextRequest

val context = memory.getContext(
    ContextRequest(
        memoryId = memoryId,
        query = "徒步",
        recentMessageLimit = 12,
        maxTokens = 2_000,
    ),
)

val recentMessages = context.recentMessages
val memoryText = context.formattedMemories()
```

## 设计边界

当前版本是可移植核心库的早期实现，重点是稳定 API、默认构建不依赖 Android、轻量本地存储和混合检索。

- `JsonFileStore` 适合轻量本地持久化和测试。更大数据量后续应接入独立 SQLite 模块。
- `SqliteStore` 是可选模块，不进入默认构建。需要时通过 `-Pmemind.includeSqlite=true` 启用。
- Room 持久化不属于默认构建。未来可以在存在 Android SDK 路径时，用可选 Android 模块提供。
- `VectorSearch` 已可注入；有已存向量时会复用，embedding 或向量检索不可用时会降级到本地文本检索。
- `InsightTree` 当前按需构建，后续会演进为 dirty flag 和增量刷新。
- `OpenAiClient` 是 OpenAI 兼容接口适配器，生产环境建议由宿主自行管理 API Key、代理、重试、日志脱敏和网络策略。

## 开发命令速查

```bash
# 查看可用任务
./gradlew tasks

# 构建默认的非 Android 模块
./gradlew build

# 运行测试
./gradlew :memind-mobile-core:test :memind-store-json:test

# 构建 JAR
./gradlew :memind-mobile-core:jar :memind-store-json:jar

# 测试可选 SQLite store
./gradlew -Pmemind.includeSqlite=true :memind-store-sqlite:test
```

## License

当前仓库尚未声明开源许可证。正式对外开源前，请先补充 `LICENSE` 文件，并在本节标明许可证类型。
