# 进度日志

## 会话：2026-05-27

### 阶段 1：规划文件初始化与原版对照整理
- **状态：** complete
- **开始时间：** 2026-05-27 Asia/Shanghai
- 执行的操作：
  - 读取 `planning-with-files-zh` 技能说明。
  - 检查项目内是否已有 `task_plan.md`、`findings.md`、`progress.md`。
  - 读取旧的 `TASK_PLAN.md` 作为背景。
  - 发现根目录 `task_plan.md` 与 `TASK_PLAN.md` 存在大小写冲突风险。
  - 创建 scoped 规划目录 `.planning/memind_mobile_core/`。
- 创建/修改的文件：
  - `.planning/.active_plan`
  - `.planning/memind_mobile_core/task_plan.md`
  - `.planning/memind_mobile_core/findings.md`
  - `.planning/memind_mobile_core/progress.md`

### 下一步
- **状态：** complete
- 执行的操作：
  - 已从阶段 2 开始实施数据模型与存储重构。
  - 已重新读取 `.planning/memind_mobile_core/task_plan.md` 和 `.planning/memind_mobile_core/findings.md`。
  - 阶段 2 的数据模型、RoomStore、InMemoryStore 已经和当前代码对齐。
  - 阶段 3 新增 `PendingConversationBuffer`、`RecentConversationBuffer` 和 `LocalRuleCommitDetector`。
  - `addMessage` 已改为写入 pending/recent buffer，`commit` 已实现 drain pending -> ConversationSegment -> RawData/MemoryItem。
  - 单元测试已改为验证 commit 后可检索，并新增自动 commit metadata 测试。

### 阶段 4：移动版抽取 pipeline
- **状态：** complete
- 执行的操作：
  - 新增 `MemoryExtractor` 接口。
  - 新增 `RuleBasedMemoryExtractor` 作为离线 fallback。
  - 将独立文本抽取和 commit 后的对话段抽取从 `DefaultMemory` 迁移到 extractor。
  - 实现精确 contentHash 去重，并补充重复抽取测试。
  - 将构建系统对齐 PokeClaw：Gradle Wrapper 9.3.1、AGP 9.1.0、Kotlin 2.1.21、compileSdk 36.1、Daemon JVM 21。
  - 新增 `LlmJsonMemoryExtractor`，默认关闭，显式 `withLlmExtraction()` 时启用，失败自动降级规则 extractor。
  - LLM JSON 抽取限制了输入字符数、最大 item 数和 `timeoutMs`。
  - 新增 `TemporalNormalizer`，支持 yyyy-MM-dd / yyyy/MM/dd、消息时间和 observedAt 回退。
  - 新增 `MemoryDeduplicator`，支持精确 hash 去重和可选 embedding/vector 近似去重。
  - `ExtractionConfig` 增加 LLM、语义去重、foresight、输入长度和抽取条数控制。

### 阶段 5：混合检索与上下文接口
- **状态：** pending
- 下一步：
  - 实现 `getContext(ContextRequest)`，组合 recent messages 和 retrieval result。
  - 将 retrieve 扩展为 keyword + vector + RRF 的 Simple hybrid 策略。
  - 为 rawData/insight tier 预留检索合并入口。

### 构建拆分：默认 Kotlin/JVM core + store-json
- **状态：** complete
- 执行的操作：
  - `settings.gradle.kts` 默认只 include `:memind-mobile-core` 和 `:memind-store-json`。
  - `memind-mobile-core` 改为 Kotlin/JVM jar 模块，并从默认 source set 排除 `store/room` 源码。
  - 新增 `memind-store-json`，用 JSONL 文件实现 `MemoryStore`，覆盖 item、rawData、insight、buffer 基础路径。
  - 为需要 JSON 持久化的数据模型补充 kotlinx.serialization 注解。
  - README 中英文版本改为说明默认 Android-free 构建和 JAR 产物。

### 可选 SQLite 持久化模块
- **状态：** complete
- 执行的操作：
  - 新增 `memind-store-sqlite` Kotlin/JVM 模块。
  - 新增 `SqliteStore`，基于 `org.xerial:sqlite-jdbc` 实现 `MemoryStore`。
  - SQLite 模块不进入默认构建；仅当传入 `-Pmemind.includeSqlite=true` 时 include。
  - 补充 SQLite store 的 item、rawData、insight、buffer 基础契约测试。

## 测试结果
| 测试 | 输入 | 预期结果 | 实际结果 | 状态 |
|------|------|---------|---------|------|
| 未运行 | 本次仅制定计划 | 不改动业务代码 | 已创建规划文件 | 通过 |
| `gradle :memind-mobile-core:test` | 阶段 3/4 buffer、commit、extractor 变更 | 单元测试执行 | 失败于 Gradle 版本检查：本机 Gradle 7.5.1 低于旧 AGP 8.7.3 要求的 8.9 | 阻塞 |
| `./gradlew --version` | PokeClaw 构建系统对齐 | 使用 Gradle 9.3.1 | Gradle Wrapper 下载并输出 9.3.1 成功；首次 test 发现当前 JVM 11 低于 Gradle 9 要求 | 部分通过 |
| `./gradlew :memind-mobile-core:test` | PokeClaw 构建系统对齐 | 测试执行 | 已越过 Gradle/JVM 检查，失败于 AGP 9.1 与显式 `org.jetbrains.kotlin.android` 重复注册 `kotlin` extension | 修复中 |
| `./gradlew :memind-mobile-core:test` | AGP 9 built-in Kotlin 适配 | 测试执行 | 失败于 kapt 不兼容 built-in Kotlin | 修复中 |
| `./gradlew :memind-mobile-core:test` | KSP + AGP 9 built-in Kotlin 适配 | 测试执行 | 失败于 KSP 通过 kotlin.sourceSets 添加生成目录；AGP 提示可设置 `android.disallowKotlinSourceSets=false` 兼容 | 修复中 |
| `ANDROID_HOME=/Users/gauss/Library/Android/sdk GRADLE_USER_HOME=.gradle-build ./gradlew :memind-mobile-core:test` | PokeClaw 构建系统对齐后完整验证 | 单元测试执行 | BUILD SUCCESSFUL，17 个任务执行 | 通过 |
| `ANDROID_HOME=/Users/gauss/Library/Android/sdk GRADLE_USER_HOME=.gradle-build ./gradlew :memind-mobile-core:test` | 阶段 4 LLM JSON、时间解析、语义去重变更 | 单元测试执行 | BUILD SUCCESSFUL，17 个任务执行 | 通过 |
| `GRADLE_USER_HOME=/opt/code/memind-mobile/.gradle-build ./gradlew build` | 默认 core + store-json 构建 | 不设置 `ANDROID_HOME`，构建两个 JVM 模块 | BUILD SUCCESSFUL，执行 core/store-json 编译与测试，不触发 Android SDK 检查 | 通过 |
| `GRADLE_USER_HOME=/opt/code/memind-mobile/.gradle-build ./gradlew :memind-mobile-core:publishReleasePublicationToLocalBuildRepository :memind-store-json:publishReleasePublicationToLocalBuildRepository` | 默认模块本地 Maven 发布 | 不设置 `ANDROID_HOME`，发布两个 JVM 模块 | BUILD SUCCESSFUL，生成 core/store-json 的本地 Maven 产物 | 通过 |
| `GRADLE_USER_HOME=/opt/code/memind-mobile/.gradle-build ./gradlew build` | 新增 SQLite 可选模块后默认构建验证 | 默认不传 SQLite 开关 | BUILD SUCCESSFUL，只执行 core/store-json，不编译 SQLite | 通过 |
| `GRADLE_USER_HOME=/opt/code/memind-mobile/.gradle-build ./gradlew -Pmemind.includeSqlite=true :memind-store-sqlite:test :memind-store-sqlite:publishReleasePublicationToLocalBuildRepository` | SQLite 可选模块验证 | 显式 include SQLite store，运行测试并发布本地 Maven 产物 | BUILD SUCCESSFUL，SQLite store 单元测试和本地发布通过 | 通过 |

## 错误日志
| 时间戳 | 错误 | 尝试次数 | 解决方案 |
|--------|------|---------|---------|
| 2026-05-27 | 大小写文件名冲突：根目录 `task_plan.md` 映射到既有 `TASK_PLAN.md` | 1 | 改用 `.planning/memind_mobile_core/` scoped plan，并恢复旧 `TASK_PLAN.md` |
| 2026-05-27 | 项目目录没有 `./gradlew`，无法先跑 wrapper 基线测试 | 1 | 记录环境限制；后续尝试系统 `gradle` 或仅做编译级代码检查 |
| 2026-05-27 | 系统 `gradle test` 无法解析 `com.android.library:8.7.3` 插件 | 1 | 记录为环境/依赖解析问题；继续做静态修复，最终再次尝试验证 |
| 2026-05-27 | 使用 JDK 18 后插件可解析，但系统 Gradle 7.5.1 低于 AGP 8.7.3 要求的 Gradle 8.9 | 1 | 后续使用临时 Gradle 8.9 或补 wrapper 进行验证 |
| 2026-05-27 | 阶段 3/4 变更后再次运行 `gradle :memind-mobile-core:test`，仍因 Gradle 7.5.1 低于 8.9 失败 | 3 | 代码已完成 `git diff --check` 静态检查；需要可用 Gradle 8.9+ 后再执行测试 |
| 2026-05-27 | 新增 Gradle 9.3.1 wrapper 后运行 `./gradlew :memind-mobile-core:test`，失败于当前 JVM 11 低于 Gradle 9 要求 | 1 | 已补充与 PokeClaw 对齐的 `gradle/gradle-daemon-jvm.properties`，指定 Daemon JVM 21 |
| 2026-05-27 | 对齐 Daemon JVM 后运行 `./gradlew :memind-mobile-core:test`，失败于 AGP 9.1 下重复应用 Kotlin Android 插件 | 1 | 按 PokeClaw 配置移除显式 `org.jetbrains.kotlin.android` 插件 |
| 2026-05-27 | 移除 Kotlin Android 插件后运行 `./gradlew :memind-mobile-core:test`，失败于 kapt 不兼容 AGP 9 built-in Kotlin | 1 | 将 Room compiler 从 kapt 迁移到 KSP `2.1.21-2.0.2` |
| 2026-05-27 | 迁移 KSP 后运行 `./gradlew :memind-mobile-core:test`，失败于 AGP 9 默认禁止 kotlin.sourceSets 添加 KSP 生成目录 | 1 | 按 AGP 错误建议设置 `android.disallowKotlinSourceSets=false` |
| 2026-05-27 | 设置 KSP/AGP 兼容属性后运行 test，失败于本仓库没有 Android SDK 路径 | 1 | 使用 PokeClaw 同一 SDK 路径设置 `ANDROID_HOME=/Users/gauss/Library/Android/sdk` 后测试通过 |
| 2026-05-27 | 默认 JVM 构建初次运行失败于本机找不到 Java 17 toolchain | 1 | 模块 toolchain 对齐 Java 21，Java/Kotlin target 仍固定为 JVM 17 |
| 2026-05-27 | Java toolchain 改为 21 后，Java/Kotlin target 不一致 | 1 | 显式设置 `sourceCompatibility` 和 `targetCompatibility` 为 Java 17 |
| 2026-05-27 | SQLite 模块初版被加入默认 settings，不符合“类似 Room 可选编译”的要求 | 1 | 改为通过 `-Pmemind.includeSqlite=true` 条件 include，默认构建不包含 SQLite |

## 五问重启检查
| 问题 | 答案 |
|------|------|
| 我在哪里？ | 阶段 4 已完成；默认构建已拆为 Android-free core + store-json；进入阶段 5 前 |
| 我要去哪里？ | 阶段 5：混合检索与上下文接口 |
| 目标是什么？ | 完善 `memind-mobile`，在移动性能约束下接近原版 Memind 核心记忆能力 |
| 我学到了什么？ | 见 `.planning/memind_mobile_core/findings.md` |
| 我做了什么？ | 完成阶段 2/3，完成阶段 4 的 LLM JSON extractor、规则 fallback、时间解析、hash/语义去重和 foresight 开关；完成默认 JVM core + JSON store 构建拆分 |

---
*每个阶段完成后或遇到错误时更新此文件*
