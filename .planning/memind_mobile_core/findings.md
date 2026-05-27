# 发现与决策

## 需求
- 用户希望制定一个后续计划，完善 `memind-mobile`。
- 目标是让移动版功能尽量接近原版 `/opt/code/memind` 的核心思想和核心算法。
- 同时必须考虑 Android/移动设备性能、功耗、弱网、存储和后台限制。

## 当前移动版状态
- `memind-mobile-core` 已有 Kotlin API 骨架：`Memory`、`DefaultMemory`、`MemoryBuilder`、model、store、search、insight、llm。
- 当前 `addMessage` 已写入 pending/recent buffer，不再直接生成最终 `MemoryItem`。
- 当前 `commit` 已能 drain pending buffer，生成 `ConversationSegment`，并通过轻量规则路径保存 `RawData` 与 `MemoryItem`。
- 当前已有 `LocalRuleCommitDetector`，支持显式 metadata 或 pending 条数阈值触发自动 commit。
- 当前已有 `MemoryExtractor` 接口和 `RuleBasedMemoryExtractor`，独立文本和对话段抽取已从 `DefaultMemory` 中拆出。
- 当前已有 `LlmJsonMemoryExtractor`，默认关闭，显式启用时会调用 ChatClient 生成受控 JSON，并在失败时降级到规则 extractor。
- 当前规则 extractor 已支持精确 contentHash 去重；开启 `enableSemanticDedup` 且注入 vectorSearch 后可使用 embedding/vector 做近似去重。
- 当前已有轻量 `TemporalNormalizer`，支持文本日期、消息时间和 observedAt 回退。
- 当前 foresight 是可选标记，默认关闭；启用后规则抽取会生成 `MemoryItemType.FORESIGHT`。
- 当前默认构建只包含 `memind-mobile-core` 和 `memind-store-json` 两个 Kotlin/JVM 模块，不依赖 Android SDK。
- 当前 `JsonFileStore` 提供 Android-free 的 JSONL 持久化；Room/Android 持久化已从默认构建路径移除，后续可作为可选模块重新接入。
- 当前新增 `SqliteStore` 作为可选 Kotlin/JVM SQLite 持久化模块，需要 `-Pmemind.includeSqlite=true` 才会 include 和编译。
- 当前 `retrieve` 已支持 BM25 风格本地文本检索、可选向量召回、RRF 融合、scope/category 过滤和 Deep-lite 本地扩展/重排。
- 当前 `getInsightTree` 是按 item 文本临时拼装 leaf/branch/root，没有原版 insight point、group、version、flush/scheduler。
- `RoomStore` 源码曾覆盖 items、raw_data、insights、buffer_messages 和 vector_metadata 的基础表与读写路径，但当前默认构建不编译 Android/Room 路径。
- `JsonFileStore` 已覆盖 item、rawData、insight、buffer 的基础 JSONL 持久化路径，是当前默认持久化模块。

## 原版核心能力摘要
- 入口 API 包含 extraction、context、retrieval、deletion、flush/invalidate、memory-thread runtime status。
- `addMessage` 进入上下文 pipeline，使用 pending/recent buffer 和边界检测；`commit` drain pending buffer 后触发 extraction。
- 抽取分层：raw data processing、conversation segmentation/chunking、caption、item extraction、dedup、insight scheduling。
- 数据模型包含 scope、category、contentType、sourceClient、vectorId、rawDataId、contentHash、semantic time、observedAt、metadata、item type。
- USER scope 包含 profile、behavior、event；AGENT scope 包含 tool、directive、playbook、resolution。
- 检索包含 Insight / Item / RawData 三层，Simple 为 vector + keyword + fusion，Deep 包含 query expansion、sufficiency、rerank。
- Insight Tree 是 Memind 的核心思想之一：leaf -> branch -> root 的分层理解，不只是扁平事实。
- 原版还有 item graph、temporal retrieval、memory thread、observability、metrics、resource/file/url/multimodal 等能力。

## 移动端取舍
- 必须迁移：buffer/commit、结构化 item、scope/category、hybrid retrieval、getContext、Room 持久化、Insight Tree 基础版。
- 应简化迁移：Deep retrieval、Insight Tree 构建、temporal parsing、dedup、query rewrite/rerank。
- 可延后迁移：完整 item graph、memory thread、复杂 raw resource fetch、multimodal resource store、admin UI、MCP/server。
- 默认行为应离线可用；LLM/Embedding 作为增强能力，而不是基本能力唯一依赖。

## 推荐架构
- `buffer/`：PendingConversationBuffer、RecentConversationBuffer、CommitDetector。
- `extract/`：MemoryExtractor、RuleBasedExtractor、LlmJsonExtractor、Deduplicator、TemporalNormalizer。
- `store/`：InMemoryStore、MemoryStore 接口；默认持久化由独立 `memind-store-json` 模块提供，RoomStore 后续作为可选 Android store。
- `retrieval/`：RetrievalRequest、ContextRequest、ContextWindow、HybridRetriever、RrfMerger、Truncator。
- `insight/`：InsightLayer、InsightGenerator、InsightScheduler、DirtyInsightTracker。
- `options/`：MemoryBuildOptions、MobilePerformanceOptions。

## 性能预算建议
- 默认只在 `commit` 或显式 `flush` 时抽取，不在每条消息上进行重 LLM 调用。
- pending buffer 设置条数和 token/char 上限，超限后规则分段。
- Simple retrieval 默认本地执行，topK 控制在 5-20。
- Deep-lite 默认关闭或按需启用，必须有 timeout 和 cancellation。
- Insight 构建默认后台/手动触发，限制每批 item 数。
- Embedding 缓存和复用，避免重复请求。
- Room 查询必须分页，避免一次加载全量 memory。

## 技术决策
| 决策 | 理由 |
|------|------|
| 先做 M1 本地闭环 | 能最快让宿主 App 真正用起来 |
| 抽取 pipeline 支持规则 fallback + LLM 增强 | 移动端需要离线/弱网降级 |
| 检索先实现 Simple hybrid | 复刻原版最关键召回思路，成本可控 |
| Insight Tree 使用 dirty flag + flush | 避免 `getInsightTree` 每次全量重建 |
| graph/thread 延后 | 原版实现复杂，移动收益不如前几项直接 |
| 构建系统对齐 PokeClaw：Gradle 9.3.1、AGP 9.1.0、Kotlin 2.1.21、compileSdk 36.1、Daemon JVM 21 | 方便 PokeClaw 通过源码模块或本地 Maven 产物集成，减少版本协调成本 |
| 默认构建只编译 core + store-json | 让基础库成为通用 Kotlin/JVM 产物，避免无 Android SDK 环境下被 Room/AGP 阻塞 |
| RoomStore 不放在默认 core jar 中编译 | Room 依赖 Android/SQLite/注解处理链路，应作为可选 Android 持久化模块处理 |
| SQLite store 作为显式开关模块 | 满足更通用的文件数据库持久化需求，同时保持默认构建轻量 |

## 遇到的问题
| 问题 | 解决方案 |
|------|---------|
| 原版能力面很广，移动端不能完整同步服务端级复杂度 | 采用分层迁移：核心闭环、增强能力、实验模块 |
| 当前移动端 API 返回类型和原版语义有差异 | 后续通过新增 Request/Result 类型逐步兼容，不一次性破坏 API |
| 大小写文件名冲突导致根目录 `task_plan.md` 会覆盖 `TASK_PLAN.md` | 使用 `.planning/memind_mobile_core/` 作为 scoped planning 目录 |
| 当前项目没有 Gradle wrapper，系统 Gradle 无法解析 Android Gradle Plugin 8.7.3 | 本轮先记录验证限制；后续建议补 wrapper 或固定可解析的 AGP/Gradle 组合 |
| AGP 9 built-in Kotlin 与 kapt 不兼容 | 已迁移 Room compiler 到 KSP，并设置临时兼容属性 `android.disallowKotlinSourceSets=false` |
| 默认构建不应强制要求 Android SDK | 已将默认 settings 收窄到 `:memind-mobile-core` 与 `:memind-store-json`，core 改为 Kotlin/JVM jar，并排除 Room 源码 |
| 本机没有 Java 17 toolchain | 使用 PokeClaw 对齐的 Java 21 daemon/toolchain 编译，同时将 Java/Kotlin bytecode target 固定为 JVM 17 |
| SQLite 不应默认编译进核心 | 已通过 `memind.includeSqlite` Gradle 属性控制 include，默认 `./gradlew build` 不触发 SQLite 模块 |

## 资源
- 原版项目：`/opt/code/memind`
- 移动版项目：`/opt/code/memind/memind-mobile`
- 旧移动计划：`/opt/code/memind/memind-mobile/TASK_PLAN.md`

## 视觉/浏览器发现
- 未使用浏览器或视觉检查。

---
*每执行2次查看/浏览器/搜索操作后更新此文件*
