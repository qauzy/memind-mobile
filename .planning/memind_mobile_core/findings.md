# 发现与决策

## 需求
- 用户希望制定一个后续计划，完善 `memind-mobile`。
- 目标是让移动版功能尽量接近原版 `/opt/code/memind` 的核心思想和核心算法。
- 同时必须考虑 Android/移动设备性能、功耗、弱网、存储和后台限制。

## 当前移动版状态
- `memind-mobile-core` 已有 Kotlin API 骨架：`Memory`、`DefaultMemory`、`MemoryBuilder`、model、store、search、insight、llm。
- 当前 `addMessage` 直接把消息原文保存为 `MemoryItem`，没有 pending buffer 和结构化抽取。
- 当前 `commit` 基本是 no-op，只返回成功。
- 当前 `retrieve` 只做简单文本匹配，没有 vector、RRF、scope/category 过滤、deep retrieval。
- 当前 `getInsightTree` 是按 item 文本临时拼装 leaf/branch/root，没有原版 insight point、group、version、flush/scheduler。
- `build.gradle.kts` 已引入 Room，但还没有真实 `RoomStore`。

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
- `store/`：RoomStore、InMemoryStore、ItemDao、RawDataDao、InsightDao、BufferDao。
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

## 遇到的问题
| 问题 | 解决方案 |
|------|---------|
| 原版能力面很广，移动端不能完整同步服务端级复杂度 | 采用分层迁移：核心闭环、增强能力、实验模块 |
| 当前移动端 API 返回类型和原版语义有差异 | 后续通过新增 Request/Result 类型逐步兼容，不一次性破坏 API |
| 大小写文件名冲突导致根目录 `task_plan.md` 会覆盖 `TASK_PLAN.md` | 使用 `.planning/memind_mobile_core/` 作为 scoped planning 目录 |
| 当前项目没有 Gradle wrapper，系统 Gradle 无法解析 Android Gradle Plugin 8.7.3 | 本轮先记录验证限制；后续建议补 wrapper 或固定可解析的 AGP/Gradle 组合 |

## 资源
- 原版项目：`/opt/code/memind`
- 移动版项目：`/opt/code/memind/memind-mobile`
- 旧移动计划：`/opt/code/memind/memind-mobile/TASK_PLAN.md`

## 视觉/浏览器发现
- 未使用浏览器或视觉检查。

---
*每执行2次查看/浏览器/搜索操作后更新此文件*
