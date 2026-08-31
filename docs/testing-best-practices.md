# Android / Compose 测试用例最佳实践

> 依据 Android 官方文档（App testing、Testing Compose 布局，2026-07 版）整理，结合本项目 MVI + Feature-first + Koin 约定。写测试前必读；Review 按下文核对。

## 0. 测试金字塔：先选对层级

| 层级 | 环境 | 特点 | 本项目对应 | 数量占比 |
|------|------|------|-----------|---------|
| **单元测试** | 本地 JVM（`app/src/test`） | 快、稳，无设备 | Reducer / 领域算法 / Repository（fake DAO）/ ViewModel | **最多** |
| **集成测试** | 设备/模拟器（`app/src/androidTest`），Robolectric 属此层 | 验证组件协同 | Room DAO、Koin 图、DataStore | 中 |
| **端到端 / UI** | 真机或模拟器 | 模拟真实用户旅程 | 关键路径（引导 → 排程 → 提醒设置 → 打卡） | **最少** |

原则：**尽量多写小而快的单测**；E2E 成本高、易碎，只覆盖核心用户路径。

---

## 1. 可测试性先行：架构与 DI

- **业务逻辑与 Android 框架分离**：纯 Kotlin 类（Reducer 必须，本项目 MVI 铁律）→ 天然可 JVM 单测。
- **依赖注入**：本项目用 Koin，测试中用 Koin Test 模块替换依赖（`declareModule` / `loadKoinModules`），或直接构造时传参——**构造注入优先**。
- **接口化边界**：DAO、时钟（`Clock`）、闹钟调度等 framework 依赖抽接口，测试替换为 fake。
- **时间源注入**：任何「当前时间」一律从外部传入（如 `Clock`），禁止测试代码里 mock `System.currentTimeMillis()` 散落点。本项目大量用到派生时间（`prepTime`、角色状态），**时钟 fake 是一等公民**。

---

## 2. Test Doubles：fake 优先

| 替身 | 适用 | 示例 |
|------|------|------|
| **Fake**（首选） | 有简化真实行为的实现，可复用 | 内存版 Repository / DAO、内存 `Clock` |
| **Stub** | 只需固定返回值 | 固定响应的数据源 |
| **Mock** | 需验证交互协议（「确实调用了 X 一次」） | MockK 验证 save 被调用 |

- 能 fake 则 fake；mock 过度 → 测试与实现强耦合，一重构就碎。
- 第三方不可测 API：包一层接口再测试（项目 `core/` 封装原则天然满足）。

---

## 3. 单元测试规范

- **测行为与公共 API，不测实现细节**（私有函数、内部节点结构）——保证重构安全。
- **一个测试一个场景**，命名表达「条件 → 预期」：`fun prepTime_isDerivedFromMealTimeMinusMinutes()`。
- **Given / When / Then** 三段结构，Arrange 显式。
- **ViewModel / StateFlow 测试**：
  - 用 `Dispatchers.setMain(testDispatcher)` + `Dispatchers.resetMain()`（封装成 `MainDispatcherRule`）。
  - 断言用 `first()` / `toList()` 收集 Flow（需要多值序列/超时断言时可引入 Turbine）；避免 `runTest` 里死等。
- **协程测试**：`kotlinx-coroutines-test` 的 `runTest` + `advanceUntilIdle()` 控制虚拟时间。
- **覆盖率不是目的**：核心算法（候选生成、约束校验、打卡统计）必须全覆盖，胶水代码不强求。

---

## 4. Compose UI 测试

### 4.1 设置

```kotlin
androidTestImplementation("androidx.compose.ui:ui-test-junit4:$composeVersion")
debugImplementation("androidx.compose.ui:ui-test-manifest:$composeVersion")  // createComposeRule 必需
```

### 4.2 核心三步：Find → Action → Assert

```kotlin
class CounterTest {
    @get:Rule
    val rule = createComposeRule()   // 需 Activity 时用 createAndroidComposeRule<MainActivity>()

    @Test
    fun clickIncrements() {
        rule.setContent {
            Fast16Theme {            // 与生产一致的主题，测试不可裸 Material
                Counter(count = 0, onIncrement = {})
            }
        }
        rule.onNodeWithText("＋").performClick()          // Action
        rule.onNodeWithText("1").assertIsDisplayed()      // Assert
    }
}
```

- **注入假 State**：`setContent { Screen(uiState = fakeUiState) }`，把 Screen 当纯函数黑盒测——这正是状态提升（见 compose-performance.md §2）带来的可测性红利。
- **优先语义匹配器**（`onNodeWithText` / `onNodeWithContentDescription`），跨实现稳定；实现细节 tag（`onNodeWithTag`）作为补充，且 tag 必须用 `Modifier.testTag` 显式标注。
- **无障碍即语义**：`onNodeWithContentDescription` 断言顺带覆盖 TalkBack 路径，按钮/图标必须给 contentDescription。

### 4.3 同步与常见坑

- Compose 测试自动等待主线程空闲后才断言；但**自己的后台协程/线程不会自动等待**——用注入 dispatcher + test dispatcher 同步，或 `rule.waitUntil { ... }`。
- 含**无限动画**（循环转圈、呼吸灯）的页面会让「等待空闲」永不满足、测试挂起超时——先在测试态关掉动画（或注入可暂停的动画/时钟开关），断言改用 `waitUntil { 条件 }` 条件轮询。
- 弹窗（Dialog）内容在独立窗口中，Compose 测试 API 仍可直接查找其节点；若目标文本被**合并**进父节点语义（如整行可点击的 ListItem 内文本），改用 `onNodeWithText(..., useUnmergedTree = true)` 或匹配父节点。

### 4.4 UI 测试写什么

- **行为测试**：交互 → 状态变化（点击打卡、切换方案 tab、空态 → 有数据）。
- **测用户可见行为，而非实现细节**。
- **关键用户旅程走 E2E**（`createAndroidComposeRule` + Koin 测试模块），少量精维护。

---

## 5. 集成 / 数据层测试

- **Room**：`Room.inMemoryDatabaseBuilder(...)` 建内存库；schema 迁移必须用官方 `MigrationTestHelper` 写迁移测试（本项目改表必须升 version + 写迁移，见 AGENTS.md §6）。
- **DataStore**：临时文件 + `testScope`；存取对称断言。
- **Koin 模块**：`checkModules()` / 启动全图验证依赖可解析。

---

## 6. 执行与 CI

- 本项目验证命令（AGENTS.md §5）：`:app:testDebugUnitTest`（单测）、`:app:lintDebug`；仪器测试用 `connectedDebugAndroidTest` 或 Gradle Managed Devices。
- 提交前至少跑单测 + lint；E2E/多尺寸（默认/最小/最大窗口）在集成阶段统一验证。
- 测试**不应依赖执行顺序、网络、真实时间**；需要随机性时注入受控 seed。

---

## 参考来源

- https://developer.android.com/training/testing（测试基础 / 本地测试 / 插桩测试 / UI 测试）
- https://developer.android.com/develop/ui/compose/testing（测试 Compose 布局）
- https://github.com/android/testing-samples（官方示例）
