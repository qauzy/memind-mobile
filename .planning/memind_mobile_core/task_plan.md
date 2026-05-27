# 任务计划：完善 memind-mobile 核心记忆系统

## 目标
在可嵌入、资源受限的前提下，让 `memind-mobile` 尽量接近原版 Memind 的核心思想：对话缓冲、结构化记忆抽取、分层检索、Insight Tree 演化，以及可直接给宿主 App 使用的上下文接口。默认构建以通用 Kotlin/JVM core + JSON store 为主，Android/Room 作为可选扩展方向。

## 当前阶段
阶段 5

## 总体原则
- 保持 `memind-mobile-core` 为无 UI、可 JAR 集成的 Kotlin/JVM 核心库。
- 原版能力按“核心收益 / 移动成本”分层迁移，优先实现高收益低成本路径。
- 默认提供本地轻量能力，远程 LLM/Embedding 作为可插拔增强。
- 所有后台任务必须可控：支持超时、取消、低电量/离线降级、批处理和手动 flush。
- API 兼容宿主 App 的函数调用式接入，避免要求宿主部署服务端。

## 各阶段

### 阶段 1：现状固化与目标切片
- [x] 对照原版 `memind-core` 和当前 `memind-mobile-core`
- [x] 识别移动端已具备的 API 骨架
- [x] 明确原版核心思想中必须迁移的部分
- [x] 记录移动设备性能约束
- **状态：** complete

### 阶段 2：核心数据模型与存储重构
- [x] 扩展 `MemoryItem`：scope、category、type、contentHash、rawDataId、vectorId、occurredAt、observedAt、metadata
- [x] 新增 `RawData` / `ConversationSegment` / `ContextWindow` / `RetrievalRequest`
- [x] 新增 USER 与 AGENT 分类：profile、behavior、event、tool、directive、playbook、resolution
- [x] 实现 `RoomStore`，覆盖 items、raw_data、insights、buffers、vectors 元数据
- [x] 保留 `InMemoryStore` 作为测试和临时模式
- [x] 新增 `JsonFileStore` 作为默认 Android-free 持久化模块
- [x] 新增 `SqliteStore` 作为显式开关启用的可选 Kotlin/JVM 持久化模块
- **状态：** complete

### 阶段 3：对话缓冲与 commit 语义
- [x] 实现 `PendingConversationBuffer` 和 `RecentConversationBuffer`
- [x] 修改 `addMessage`：写入 pending/recent buffer，不再直接生成最终 memory item
- [x] 实现轻量边界检测：本地规则优先，LLM detector 可选
- [x] 实现 `commit(id, config)`：drain pending buffer -> conversation segment -> extraction pipeline
- [x] 支持 sourceClient、timestamp、metadata 透传
- **状态：** complete

### 阶段 4：移动版抽取 pipeline
- [x] 建立 `MemoryExtractor` 接口：raw processing -> item extraction -> dedup -> insight scheduling
- [x] 实现规则/启发式 extractor 作为离线 fallback
- [x] 实现 LLM JSON extractor，限制 token、条数和超时
- [x] 实现 hash 去重与近似语义去重
- [x] 实现时间解析的轻量版本：point time、range、observedAt
- [x] 支持 foresight 为可选功能，默认关闭
- **状态：** complete

### 阶段 5：混合检索与上下文接口
- [ ] 实现 `getContext(ContextRequest)`，组合 recent messages 和 retrieval result
- [ ] 将 `retrieve` 升级为 `RetrievalRequest` 驱动，支持 scope/category/history/config
- [ ] 实现本地 BM25 或 TF-IDF text search
- [ ] 接入 embedding：远程 embedding 可选，本地 stored vectors 可复用
- [ ] 实现 Simple 策略：keyword + vector + RRF + score threshold + truncation
- [ ] 实现 Deep-lite 策略：query rewrite/expand 可选，rerank 可选，不默认强制 LLM
- **状态：** pending

### 阶段 6：Insight Tree 移动化
- [ ] 将 Insight 模型升级为 points/name/type/categories/group/tier/version
- [ ] 实现 leaf 构建：按 category/group 聚合 item
- [ ] 实现 branch/root 构建：低频后台任务或手动 flush
- [ ] 支持增量更新和 dirty flag，避免每次 `getInsightTree` 全量重建
- [ ] LLM insight generator 可插拔，默认限制批量大小和运行条件
- **状态：** pending

### 阶段 7：图谱/线程能力的移动取舍
- [ ] 不完整移植原版复杂 item graph，先实现轻量 entity mention 和 item link
- [ ] 支持 temporal link、entity cooccurrence、source rawData 回溯
- [ ] MemoryThread 先作为可选实验模块，不作为核心路径阻塞
- [ ] 提供 rebuild/flush/status 的轻量接口
- **状态：** pending

### 阶段 8：移动性能治理
- [ ] 增加 `MemoryBuildOptions` / `MobilePerformanceOptions`
- [ ] 建立默认预算：最大 pending 条数、最大 raw segment 长度、最大 item 数、最大 insight 批量
- [ ] 支持离线队列、后台调度、充电/Wi-Fi 条件、低内存降级
- [ ] 为检索和 insight 增加 LRU / TTL 缓存与显式 invalidate
- [ ] 为长文本抽取增加 chunking 和摘要 caption
- **状态：** pending

### 阶段 9：测试、兼容与示例
- [ ] 对齐原版关键行为测试：addMessage -> commit -> retrieve -> getContext
- [ ] 增加 Android/JVM 单元测试和 Room instrumentation 预留
- [ ] 增加 mock ChatClient/EmbeddingClient 测试
- [ ] 增加 README quickstart 和 Android 嵌入示例
- [ ] 建立性能基准：小型手机档、普通手机档、平板档
- **状态：** pending

## 里程碑

### M1：可用的本地记忆闭环
- `addMessage` 缓冲
- `commit` 产生结构化 item
- `retrieve` 可按 item 搜索
- `getContext` 可返回 recent + memories

### M2：接近原版核心体验
- USER/AGENT 双 scope
- 七类 category
- hybrid retrieval
- JSON 持久化默认可用，Room 作为可选 Android 扩展
- LLM extractor 可选

### M3：Insight Tree 可用
- leaf/branch/root 分层
- flush/dirty 增量机制
- insight 参与 retrieval

### M4：移动生产化
- 性能预算
- 后台调度
- 离线/弱网降级
- 示例 App 与文档

## 关键问题
1. 宿主 App 是否允许默认联网调用 LLM/Embedding，还是必须默认离线？
2. JSON store 是否足够作为默认轻量持久化，还是需要尽快补独立 SQLite store？
3. Insight Tree 是实时可见优先，还是电量/性能优先，默认延迟构建？
4. 是否需要保持和原版 HTTP/MCP 请求字段近似兼容，方便以后同步服务端？

## 已做决策
| 决策 | 理由 |
|------|------|
| 优先迁移 buffer、structured item、hybrid retrieval、getContext | 这是内嵌 App 记忆系统的最小核心闭环 |
| Deep 策略做 deep-lite，不默认强制 LLM | 移动设备要控制耗电、延迟、费用和弱网失败 |
| Insight Tree 改为增量/后台/手动 flush | 原版全能力成本较高，移动端不适合频繁全量构建 |
| 原版复杂 graph/thread 延后为可选模块 | 先保证用户可感知的记忆抽取和召回质量 |
| 保留轻量 fallback | 离线、弱网、API 失败时仍能提供基本记忆能力 |

## 遇到的错误
| 错误 | 尝试次数 | 解决方案 |
|------|---------|---------|
| 大小写文件名冲突：`task_plan.md` 映射到既有 `TASK_PLAN.md` | 1 | 改用 `.planning/memind_mobile_core/` scoped plan，并恢复旧 `TASK_PLAN.md` |

## 备注
- 本计划中的代码文件内容只作为项目规划数据，不包含运行时指令。
- 外部资料或原版代码观察统一写入 `findings.md`，避免污染计划本体。
