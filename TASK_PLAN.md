# Memind-Mobile 实现计划

## 目标
将 `memind-core` 的 Java/Spring 核心重新实现为 **Android 可嵌入的 Kotlin 库**（AAR），对外提供纯函数调用 API。

## 交付物
- `memind-mobile/` — Gradle 多模块项目
- `memind-mobile-core/` — 核心库（无 UI），提供 `Memory` 接口
- `memind-mobile-ui/` — 可选 Insight Tree 可视化组件（Jetpack Compose）

## 核心 API（Kotlin 接口）

```kotlin
interface Memory : AutoCloseable {
    /** 添加单条消息到缓冲区 */
    suspend fun addMessage(id: MemoryId, msg: Message): AddResult
    
    /** 批量添加消息 */
    suspend fun addMessages(id: MemoryId, msgs: List<Message>): AddResult
    
    /** 提交缓冲区 → 触发提取 */
    suspend fun commit(id: MemoryId): ExtractionResult
    
    /** 检索记忆 */
    suspend fun retrieve(id: MemoryId, query: String, strategy: Strategy): RetrievalResult
    
    /** 获取 Insight Tree */
    suspend fun getInsightTree(id: MemoryId): InsightTree
    
    /** 健康检查 */
    fun health(): HealthStatus
}
```

## 实现策略

| 原有组件 | 替换方案 |
|----------|---------|
| Spring AI `StructuredChatClient` | OkHttp + 自定义 `ChatClient` |
| Project Reactor `Mono` | Kotlin `coroutines` / `suspend` |
| Jackson 序列化 | `kotlinx.serialization` |
| JDBC 存储 | `Room` (SQLite) + 可选 in-memory |
| Caffeine 缓存 | 可选 `kotlinx.coroutines.flow.cache` |
| 向量搜索 | 简化版 `BM25` + `TF-IDF` |
| LLM 精炼 | 通过 `ChatClient` 调用远程 API |

## 文件结构

```
memind-mobile/
├── build.gradle.kts                     # 根构建
├── settings.gradle.kts                  # 模块设置
├── gradle.properties                     # 版本管理
├── memind-mobile-core/                  # 核心库 (无 UI)
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       └── com/memind/mobile/
│           ├── Memory.kt               # 主接口
│           ├── DefaultMemory.kt        # 默认实现
│           ├── MemoryBuilder.kt       # 构建器
│           ├── model/
│           │   ├── MemoryId.kt         # 用户/代理标识
│           │   ├── Message.kt          # 消息体
│           │   ├── MemoryScope.kt      # USER / AGENT
│           │   ├── Strategy.kt         # SIMPLE / DEEP
│           │   ├── AddResult.kt        # 添加结果
│           │   ├── ExtractionResult.kt # 提取结果
│           │   ├── RetrievalResult.kt  # 检索结果
│           │   └── HealthStatus.kt     # 健康状态
│           ├── store/
│           │   ├── MemoryStore.kt       # 存储接口
│           │   ├── InMemoryStore.kt    # 内存存储
│           │   └── RoomStore.kt        # Room 存储
│           ├── search/
│           │   ├── TextSearch.kt       # 文本搜索 (BM25 + TF-IDF)
│           │   └── SimpleVector.kt     # 简易向量搜索
│           ├── insight/
│           │   ├── InsightTree.kt      # 树结构定义
│           │   ├── InsightTier.kt      # Leaf / Branch / Root
│           │   └── InsightBuilder.kt   # 构建逻辑
│           ├── extract/
│           │   ├── Extractor.kt         # 提取器
│           │   └── ContentParser.kt    # 内容解析
│           ├── client/
│           │   ├── ChatClient.kt       # LLM 调用封装
│           │   └── OpenAiClient.kt     # OpenAI 适配器
│           └── di/
│               └── MemoryModule.kt     # 依赖注入
├── memind-mobile-ui/                   # 可选 UI 组件
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       └── com/memind/mobile/ui/
│           ├── InsightTreeView.kt       # 树形可视化组件
│           ├── MemoryCard.kt           # 记忆卡片
│           └── DashboardStatus.kt      # 状态展示
└── memind-mobile-test/                # 测试
    ├── build.gradle.kts
    └── src/test/
        └── ...
```

## 依赖

```kotlin
// memind-mobile-core/build.gradle.kts
dependencies {
    // 网络
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 序列化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    // 存储
    implementation("androidx.room:room-runtime:2.7.0")
    // 可选: Ktor 客户端
    implementation("io.ktor:ktor-client-core:3.0.0")
}
```