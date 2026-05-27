# AGENT 约束规则

## 工作边界
- 只能修改当前仓库内的代码和项目文件。
- 禁止修改系统环境、系统配置、SDK 安装、全局工具链、临时运行时或用户目录中的工具配置。
- 禁止为了验证而下载、安装或改写系统级依赖；若现有仓库环境无法验证，必须在回复中说明原因。

## 代码注释规则
- 所有新增或修改的函数必须带中文函数头注释，说明函数职责、关键输入和输出含义。
- 关键代码逻辑必须添加中文注释，说明为什么这样处理，而不只描述代码表面动作。
- 新增接口、模型和存储结构时，优先说明其与原版 Memind 核心概念的对应关系。

## 修改记录规则
- 每次代码修改都必须增加修改记录。
- 修改记录至少包含日期、修改范围、核心意图和验证情况。
- 如果验证因环境限制无法完成，必须在修改记录中明确写出阻碍原因。

## Git 管理规则
- 所有新增文件必须添加到 git 管理中，不能长期保持未跟踪状态。
- 新增文件创建后应及时执行 `git add`，让 `git status` 显示为已纳入索引的 `A` 状态。
- 若新增文件暂不应提交，必须在修改记录或回复中明确说明原因。

## 修改记录
| 日期 | 修改范围 | 核心意图 | 验证情况 |
|------|----------|----------|----------|
| 2026-05-27 | 新增本文件，补充项目级 Agent 约束 | 固化“只改仓库代码/项目文件、函数中文注释、关键逻辑注释、每次修改记录”的协作规则 | 未运行构建；本次为规则文件变更 |
| 2026-05-27 | 更新 `AGENT.md` Git 管理规则 | 固化“所有新增文件必须添加到 git 管理中”的约束 | 未运行构建；规则文件变更，并将新增文件纳入 git 索引 |
| 2026-05-27 | 为本轮新增/修改的 Kotlin 函数补充中文函数头和关键逻辑注释 | 满足函数注释与关键逻辑中文说明规则，降低后续维护成本 | 未运行构建；仅做注释和规则记录补充 |
| 2026-05-27 | 为 `MemoryStore` 接口与单元测试函数补充中文函数头 | 让存储契约和测试入口同样满足函数注释规则 | 未运行构建；仅做注释补充 |
| 2026-05-27 | 为公共接口、检索实现、Insight 构建和配置模型补齐中文函数头 | 进一步收口“所有新增/修改函数带中文函数头、关键逻辑中文说明”的约束 | 未运行构建；仅做注释和轻量清理，未触碰系统环境 |
| 2026-05-27 | 更新 core 模块 Gradle 依赖注释 | 将本轮触达的依赖分组说明改为中文，保持关键项目文件注释一致 | 未运行构建；仅做注释补充，未触碰系统环境 |
| 2026-05-27 | 新增 `.gitignore` | 忽略 Gradle/IDE/构建产物，避免打包生成文件长期处于未跟踪状态 | 未运行构建；仅新增项目忽略规则，未触碰系统环境 |
| 2026-05-27 | 配置 `memind-mobile-core` 发布 AAR 与本地 Maven 仓库产物 | 面向 `/opt/code/PokeClaw` 集成，输出可携带传递依赖的 Android 库包，并避免要求本机安装 JDK 17 toolchain | 待运行 release publish 构建；仅修改项目构建脚本与 consumer 混淆规则 |
| 2026-05-27 | 修正本地发布仓库路径类型 | 让 Gradle Kotlin DSL 使用明确的 URI，保证 release publish task 可配置通过 | 首轮构建失败暴露类型不匹配；修正后待重新运行发布构建 |
| 2026-05-27 | 为项目仓库解析增加国内 Maven 镜像 | 规避 Google Maven TLS 握手失败，保证 Room/AndroidX 依赖可在本项目内解析完成 | 第二轮构建失败于 `room-compiler` 下载；镜像连通性已用 curl HEAD 验证 |
| 2026-05-27 | 发布 `memind-mobile-core` release AAR 和本地 Maven 仓库产物 | 生成 PokeClaw 可通过 Gradle 引入的 Android 库包，并保留传递依赖元数据 | `:memind-mobile-core:publishReleasePublicationToLocalBuildRepository` 构建成功；产物位于模块 `build/outputs/aar` 和 `build/repo` |
| 2026-05-27 | 显式设置 core 模块发布坐标 | 修正本地 Maven 仓库产物从 `unspecified` 变为 PokeClaw 易引入的 `com.memind.mobile:memind-mobile-core:0.1.0` | 初次发布发现坐标不正确；修正后待重新发布 |
| 2026-05-27 | 重新发布 release 产物 | 用正确坐标重新生成 AAR、本地 Maven POM 和 Gradle module metadata | `:memind-mobile-core:clean :memind-mobile-core:publishReleasePublicationToLocalBuildRepository` 构建成功 |
| 2026-05-27 | 新增 `README.md` | 用开源项目常见结构介绍项目定位、核心思想、架构、编译发布方式和基础使用示例 | 已尝试运行 `:memind-mobile-core:test`；失败原因是本机 Gradle 7.5.1 低于 Android Gradle Plugin 8.7.3 要求的 8.9 |
| 2026-05-27 | 拆分 README 中英文版本 | 将默认 `README.md` 改为英文版，并新增 `README.zh-CN.md` 保留中文说明 | 已检查中英文入口链接与默认 README 语言；本次为文档变更，未重新运行构建 |
| 2026-05-27 | 实现阶段 3 对话缓冲与 commit 语义 | 新增 pending/recent buffer、缓冲存储读写、本地边界检测，并让 `commit` 生成对话段 RawData/MemoryItem | 已尝试运行 `:memind-mobile-core:test`；失败原因仍是本机 Gradle 7.5.1 低于 AGP 8.7.3 要求的 8.9 |
| 2026-05-27 | 启动阶段 4 抽取 pipeline | 新增 `MemoryExtractor` 和规则 extractor，把直接抽取逻辑从 `DefaultMemory` 拆出，并实现精确 hash 去重 | 已尝试运行 `:memind-mobile-core:test`；失败原因仍是本机 Gradle 7.5.1 低于 AGP 8.7.3 要求的 8.9 |
| 2026-05-27 | 对齐 PokeClaw 构建系统版本 | 将 Gradle Wrapper/AGP/Kotlin/compileSdk/Daemon JVM 对齐 PokeClaw，并把 Room compiler 从 kapt 迁移到 KSP | 使用 `ANDROID_HOME=/Users/gauss/Library/Android/sdk GRADLE_USER_HOME=.gradle-build ./gradlew :memind-mobile-core:test` 验证通过 |
| 2026-05-27 | 完成阶段 4 移动版抽取 pipeline | 新增 LLM JSON extractor、时间解析、语义去重接口和 foresight 开关，并补充相关单元测试 | 使用 `ANDROID_HOME=/Users/gauss/Library/Android/sdk GRADLE_USER_HOME=.gradle-build ./gradlew :memind-mobile-core:test` 验证通过 |
| 2026-05-27 | 默认构建拆为 Kotlin/JVM core 与 JSON store | 默认只编译 `memind-mobile-core` 和 `memind-store-json`，新增 JSONL 持久化模块，并让 Room/Android 路径退出默认构建 | 使用 `GRADLE_USER_HOME=/opt/code/memind-mobile/.gradle-build ./gradlew build` 与两个模块本地 Maven publish 验证通过，未设置 `ANDROID_HOME` |
| 2026-05-27 | 新增可选 SQLite 持久化模块 | 新增 `memind-store-sqlite` 和 `SqliteStore`，通过 `-Pmemind.includeSqlite=true` 显式 include，避免默认编译进核心或默认构建 | 默认 `./gradlew build` 验证只编译 core/store-json；SQLite 模块测试和本地 Maven 发布验证通过 |
| 2026-05-27 | 阶段 5 新增上下文窗口 API | 新增 `Memory.getContext(ContextRequest)`，组合 recent buffer 与检索结果，并加入轻量 token 预算裁剪 | 默认 `./gradlew build` 验证通过；`-Pmemind.includeSqlite=true :memind-store-sqlite:test` 回归通过 |
| 2026-05-27 | 完成阶段 5 混合检索 | 将文本检索升级为 BM25 风格，实现 keyword/vector RRF 融合、score threshold、maxResults 截断和 Deep-lite 本地扩展/重排 | `:memind-mobile-core:test`、默认 `./gradlew build` 与 SQLite 可选模块回归均验证通过 |
