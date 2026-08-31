# AGENTS.md

本文件供 AI 编码代理在 Fast16 工作区中快速建立上下文。  
遵循**渐进式披露**：先读「速览」和「文档地图」；只有进入对应任务时，才深入阅读仓库内 `docs/` 文档的指定章节。

---

## 1. 项目速览

- **Fast16**：一款 Android 本地 App「816 轻断食」，用于 8:16 间歇性断食的用餐排程与提醒。
- **核心玩法**：记录早餐时间 → 自动生成午/晚候选方案 → 用户选取/微调 → 生成当日计划 → 到点提醒「备餐 / 开吃」→ 打卡记录与统计。
- **技术基线**：Kotlin 2.2 + Jetpack Compose + Material3；`minSdk 24`、`targetSdk 37`、`applicationId com.ly.fast16`；当前为单 Gradle 模块 `:app`。构建链：Gradle 9.7.1 + AGP 9.3.0（内置 Kotlin）+ JDK 21。
- **产品基调**：深色像素风、纯本地离线、无账号/无后端、仅中文。
- **当前状态**：M0 刚起步，现有代码仍是 Android 模板（`MainActivity` + 默认主题），尚未按规格落地像素设计系统、Room、DataStore、Koin、Navigation 等。

---

## 2. 文档地图（先看这里）

规格文档已复制到仓库内 `docs/` 目录，Agent 可直接读取：

| 文档 | 内容 | 什么时候读 |
|------|------|-----------|
| [`docs/8-16饮食法App-设计方案.md`](docs/8-16饮食法App-设计方案.md) | 产品定位、需求、架构、数据模型、UI/像素规范、里程碑、决策 | 任何设计/架构决策前必读 |
| [`docs/8-16饮食法App-基建与UI骨架.md`](docs/8-16饮食法App-基建与UI骨架.md) | M0 定稿：Gradle 参数、依赖版本、路由、MVI 页面壳、Pixel 组件清单 | 搭工程、改 Gradle、搭 UI 骨架时 |
| [`docs/8-16饮食法App-完整定义.md`](docs/8-16饮食法App-完整定义.md) | DDL、DAO、DataStore Schema、算法伪码、页面组件与动效分镜 | 实现数据层、领域算法、像素组件时 |
| [`docs/8-16饮食法App-流程图.md`](docs/8-16饮食法App-流程图.md) | Mermaid 流程：主流程、候选生成、状态机、提醒、统计 | 梳理业务流或写用例时 |
| [`docs/compose-performance.md`](docs/compose-performance.md) | Compose 性能避坑：稳定性/不可变类、状态提升、延迟读取、derivedStateOf、LazyColumn key、strong skipping | 写/Review 任何 Compose UI 代码时必读 |
| [`docs/testing-best-practices.md`](docs/testing-best-practices.md) | 测试最佳实践：测试金字塔、fake 优先、MVI 分层测试、Compose UI 测试、Room 迁移测试 | 写/Review 测试代码时必读 |
| [`docs/pixel-motion-guidelines.md`](docs/pixel-motion-guidelines.md) | 像素风交互动效规范：阶跃帧、时长/节奏、整数像素、反馈手法、Compose 实现约束 | 实现/Review 交互与动效时必读 |
| [`docs/compat-api24-37.md`](docs/compat-api24-37.md) | API 24–37 兼容性避坑：各版本权限收紧、行为变更、本项目红线清单 | 写权限/通知/闹钟/系统交互代码前必读 |
| [`docs/coroutines-best-practices.md`](docs/coroutines-best-practices.md) | 协程避坑与最佳实践：结构化并发、Dispatchers 注入、取消配合、CancellationException 重抛、测试 | 写/Review 任何并发与异步代码时必读 |
| [`docs/font-licenses.md`](docs/font-licenses.md) | 内置字体授权记录：Press Start 2P / Fusion Pixel（OFL 可商用）+ 子集化方法 | 涉及字体/文案新增字符时必读 |
| [`docs/kotlin-code-style.md`](docs/kotlin-code-style.md) | Kotlin 代码规范与命名规范（官方基准）：命名、源文件组织、格式化、KDoc、惯用法、项目红线 | 写/Review 任何 Kotlin 代码时必读 |

> UI 原型（`UI原型/index.html`）原稿仍在 Obsidian Vault；如需仓库内可直接打开的原型，可再复制到 `docs/UI原型/`。

---

## 3. 按任务渐进式深入

| 任务 | 必读章节 |
|------|----------|
| 写/Review 任何 Kotlin 代码（命名/格式/KDoc/惯用法） | kotlin-code-style 全文 |
| 修改 Gradle / 依赖版本 / 工程参数 | 基建与 UI 骨架 §1 |
| 实现 Room 实体 / DAO / DataStore | 完整定义 第一部分；设计方案 §6 |
| 实现候选生成、约束校验、状态机、统计 | 完整定义 第二部分；设计方案 §7；testing-best-practices §3（算法单测） |
| 实现 Compose 页面 / 导航 / MVI 壳 | 基建与 UI 骨架 §2；设计方案 §5；compose-performance §2–§7 |
| 实现像素组件 / 主题 / 动效 | 基建与 UI 骨架 §2.1–2.4；设计方案 §8；compose-performance §1–§4；pixel-motion 全文 |
| 实现提醒 / 闹钟 / 重启自愈 | 设计方案 §5.3；流程图 §6–7；compat-api24-37 §0/§3–§5 |
| 实现记录页 / 日历 / 统计 | 设计方案 §2.1 FR-13/14、§6.4–6.5、§8.7 |
| 实现 Widget | 设计方案 §8.7；基建与 UI 骨架 §1.4 |
| 实现首次引导弹窗 | 设计方案 §8.8；流程图 §2 |

---

## 4. 当前代码状态与差距

```text
app/src/main/java/com/ly/fast16/
├── MainActivity.kt          # 模板 Hello Android，未接业务
└── ui/theme/                # 默认 Compose 主题，未落地 PixelTokens
```

已知差距（不要在未读文档时擅自“补全”）：

- `gradle/libs.versions.toml` 已按基建文档与最新可用稳定版对齐（AGP `9.3.0`、Kotlin `2.2.20`、Compose BOM `2026.08.00`、Room `3.0.1` 即 `androidx.room3:room3-*`、Koin `4.2.2` 等），依赖已通过 `sync` 验证可解析；maven 仓库走阿里云镜像。
- 尚未引入 Room、DataStore、Koin、Navigation、Glance、AlarmManager 相关代码。
- 尚未建立 `core/`、`domain/`、`data/`、`feature/` 目录结构。

---

## 5. 构建与验证

在仓库根目录执行：

```bash
./gradlew :app:assembleDebug        # 构建 debug APK
./gradlew :app:testDebugUnitTest    # 单元测试
./gradlew :app:lintDebug            # lint
```

新增纯业务逻辑时，默认应同时补 JUnit 单元测试（`app/src/test/java/`），写法遵循 [`docs/testing-best-practices.md`](docs/testing-best-practices.md)：fake 优先、测行为不测实现、时钟注入。

---

## 6. 硬性工程规则（Review 红线）

- **MVI**：每个 Screen 用 `Intent → Reducer → StateFlow`；Reducer 必须是纯函数（无 Android 依赖、无 IO、无时间源），副作用放 ViewModel/UseCase。
- **Feature-first 单模块**：`feature → domain ← data`；feature 之间禁止横向依赖；跨功能共享才进 `core/`。
- **协程纪律（禁 Java 线程锁）**：并发一律基于 Kotlin Coroutines/Flow——互斥用 `kotlinx.coroutines.sync.Mutex`、限流用 `Semaphore`、跨协程通信用 `Channel`/`StateFlow`、延时用 `delay()`。**禁止使用 Java 线程原语**：`synchronized`、`ReentrantLock`/`ReentrantReadWriteLock`、`Object.wait()/notify()`、`Thread.sleep()`、`CountDownLatch.await()`（阻塞式等待）、`Future.get()` 等——它们阻塞承载线程，会挂死同一调度器（`Dispatchers.Default` 仅 CPU 核数个线程）上的其他子 Coroutine，引发级联卡顿甚至死锁。确需阻塞调用（如 legacy IO）必须包 `withContext(Dispatchers.IO)` 且不得在锁内调用 suspend 函数。其余协程规范（Dispatchers 注入、取消配合、`CancellationException` 重抛、`NonCancellable` 用法）见 [`docs/coroutines-best-practices.md`](docs/coroutines-best-practices.md)。
- **SSOT**：UI 只订阅 DAO 的 `Flow`；一切写入走 `upsert`；禁止内存副本手动同步。
- **派生字段不落库**：`prepTime` 由 `mealTime - prepMinutes` 派生；角色状态由“当前时间 × 今日计划”派生；连续打卡由 `check_ins` 聚合派生。
- **Room 纪律**：枚举存 `Int` + TypeConverter；字符串列加长度约束；改表必须升 `schemaVersion` 并写迁移。
- **Compose Preview 必写**：每个自定义 Compose 组件、每个 Screen（页面级）都必须编写 `@Preview`，并在 Preview 中注入主题（如 `Fast16Theme`）与必要的假数据，保证可在 Android Studio / Compose Preview 中独立渲染。纯业务容器（无视觉输出）除外。
- **Compose 性能规范**：UI 代码遵循 [`docs/compose-performance.md`](docs/compose-performance.md)——UI State 用不可变 data class（集合用 `ImmutableList` 或 Wrapper）；LazyColumn 必写稳定 `key`；组合期间禁止写状态（向后写入）；频繁变化的状态用 lambda 版修饰符读取；Review 按该文档逐条核对。
- **像素风规范**：业务组件只引用 `PixelTokens`，不直接引用 M3 默认值；整数像素网格；动画走步进/阶跃；渲染用 `FilterQuality.none` + 整数倍缩放。
- **交互动效必配（像素化）**：交互类业务动效（点击反馈、打卡/勾选成功、状态切换、计数变化、页面/弹窗过渡、空态）默认必须配上，不是可选装饰；实现遵循 [`docs/pixel-motion-guidelines.md`](docs/pixel-motion-guidelines.md)——阶跃帧而非平滑插值、反馈 60–200ms 短促、整数像素位移、帧时序进 `PixelTokens`；UI 禁 squash/stretch 与模糊/渐隐过渡；不照搬 M3 motion 模式（像素风格），M3 时长仅作上限参考。
- **原生 API 兼容性（采纳前必查 min–target）**：调用任何 Android 原生 API（SDK 方法、系统服务、Manifest 特性/权限）前，必须先核对 `minSdk 24 ~ targetSdk 37` 全区间兼容性（如 `@RequiresApi` / `Build.VERSION` 门控、行为变更影响），各版本权限收紧与行为变更逐条见 [`docs/compat-api24-37.md`](docs/compat-api24-37.md)（含编码红线清单）。**只有全区间兼容，方案才可采纳**；若不兼容，必须补充兼容方案（版本分支、替代实现、降级路径、`core/` 内封装兜底），禁止在 feature 层散落裸版本判断。
- **兼容方案下沉 `core/`**：原生 API 的版本差异处理一律封装进 `core/`（如 `core/device`、`core/system`），对外暴露统一抽象入口（对象/类，如 `core/device/SystemTimeProvider`）；feature/data 层只依赖该入口，不得直接写 `Build.VERSION` 分支或 `@SuppressLint("NewApi")`。要点：
  - **判定原则**：任何 SDK 元数据要求 > `minSdk` 的 API 调用都视为「版本差异」，**即使运行时可用也必须下沉**——典型如 `java.time`（`Clock.systemDefaultZone()` / `ZoneId.systemDefault()` 元数据要求 API 26，本项目靠核心库脱糖在 API 24/25 可用，但 lint/IDE 仍报 NewApi，不能靠「实际能跑」豁免）。
  - **豁免收口**：`@SuppressLint("NewApi")` 只允许出现在该 core 封装文件内部（唯一豁免点），并必须用注释说明兼容依据（如「已启用 `isCoreLibraryDesugaringEnabled`，desugar_jdk_libs 版本」）。
  - **职责边界**：取「当前系统时间/时区」等系统能力走 `core/device/SystemTimeProvider`；纯日期换算（确定性计算，不依赖系统时间）保持纯 JVM 工具（如 `core/time/TimeUtils`，无 Android 依赖、domain 可安全引用），两者不重复造轮子。
- **本地优先/隐私**：V1 不联网、不上传、无账号；数据只存 Room + DataStore。
- **代码规范（import 优先，禁 FQN）**：任何类/函数必须**先 `import` 再使用**，禁止在代码体中写类名全量引用（FQN）——如 `com.ly.fast16.feature.home.ui.HomeViewModel()`、`androidx.compose.ui.graphics.Color.Black`、`org.koin.androidx.compose.koinViewModel()` 一律改为 import 后短名；KDoc 链接（`[ClassName]`）同样用 import 后的短名。仅 `package`/`import` 语句本身，以及描述旧包名/第三方名的注释文字可保留全限定形式。完整 Kotlin 代码规范与命名规范（命名、源文件组织、格式化、KDoc、惯用法）见 [`docs/kotlin-code-style.md`](docs/kotlin-code-style.md)。

---

## 7. 已确认的关键决策

| 决策 | 结论 |
|------|------|
| 窗口语义 | 早餐时间严格等于进食窗口起点 |
| 提醒 | V1 = 通知 + 固定轻短震动（~80ms） |
| DI | Koin（纯 Kotlin DSL） |
| 打卡存储 | Room 3.0；DataStore 只存偏好/Widget 配置 |
| 记录页 | 统计概览 + 日历合并为一个「记录」页 |
| Widget | Glance 实现，接受无动画/分钟级刷新限制 |
| 引导 | 首次启动弹像素风 8:16 说明，可跳过可重看 |

完整决策清单见 `docs/8-16饮食法App-设计方案.md` §11；若实现与本文冲突，以 `docs/` 内规格文档为源（除非用户另行确认变更）。
