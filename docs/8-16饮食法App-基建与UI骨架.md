---
标签: 饮食,Android,Compose,设计方案,基建,UI骨架
---

# 🍙 8:16 饮食法 App — 基建与 UI 骨架（M0 定稿）

> 版本：v0.4（MVI + feature-first 架构定稿）｜ 目的：开工前钉死「工程基建 + UI 骨架」，M0 只按本文搭，不改口。
> 配套：[[8-16饮食法App-设计方案]] ｜ [[8-16饮食法App-流程图]] ｜ [[8-16饮食法App-完整定义]]

---

## 1. 基建（Infrastructure）

### 1.1 工程参数

| 项 | 定值 | 说明 |
|----|------|------|
| 语言 | Kotlin 2.2.x | Compose 编译器随 Kotlin 内置（`kotlin-compose` 插件） |
| AGP | **9.3.0** | 2026-07 稳定版 |
| minSdk | **24** | Room 3.0 要求 ≥23，取 24 留余量 |
| compileSdk / targetSdk | **37** | Android 17（已定；随 Room 3.0 ≥23 约束取 minSdk 24） |
| applicationId | `com.ly.fast16` | 已定 |
| 应用英文名 | **Fast16** | 品牌名；Application 类名 `Fast16App` |
| 应用显示名 | 「**816 轻断食**」 | 无冒号，`8:16` 以 `816` 呈现 |
| 主题 | **深色 only**（V1） | 像素风优先暗底；浅色放 M2+ |
| 语言/国际化 | 仅中文，硬编码字符串 | V1 不接 i18n |

### 1.2 Gradle 版本目录（`gradle/libs.versions.toml`）

```toml
[versions]
agp = "9.3.0"
kotlin = "2.2.20"
ksp = "2.2.20-2.0.3"          # 随 Kotlin，见 KSP 发布页
composeBom = "2026.08.00"     # 月更，M0 首次 sync 锁 patch
navigation = "2.9.3"
koin = "4.2.2"
room = "3.0.1"
datastore = "1.1.7"
serialization = "1.11.0"
coroutines = "1.11.0"
lifecycle = "2.9.4"
activityCompose = "1.10.1"
coreKtx = "1.18.0"
glance = "1.1.1"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.5.1"
turbine = "1.2.1"
robolectric = "4.16"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
koin-android = { group = "io.insert-koin", name = "koin-android", version.ref = "koin" }
koin-androidx-compose = { group = "io.insert-koin", name = "koin-androidx-compose", version.ref = "koin" }
room-runtime = { group = "androidx.room3", name = "room3-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room3", name = "room3-compiler", version.ref = "room" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

> 硬确认：`agp=9.3.0`、`kotlin=2.2`、`room=3.0.x`、Compose BOM 月更；版本已随 M0 首次 sync 锁定并与实际 `gradle/libs.versions.toml` 对齐（2026-08-31）。注意：**AGP 9 内置 Kotlin，无 `kotlin-android` 插件**；**Room 3.0 走 `androidx.room3:room3-*` 坐标**（无独立 `room-ktx`，Flow 支持已内置）；测试补齐 androidx-test（junit/ext 1.3.0、espresso 3.5.1）与 Compose UI Test（BOM 管理）。

### 1.3 基建组件契约（骨架）

```kotlin
// Application + Koin（纯 DSL，无注解处理器）
class Fast16App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@Fast16App)
            modules(appModule, dataModule, schedulingModule)
        }
    }
}

// 调度骨架（接口先行，实现 M1 填充）
interface PlanScheduler {
    fun schedule(plan: MealPlan, meals: List<Meal>)   // 排程 PREP/EATING 闹钟
    fun cancel(planId: Long)                          // 撤销该计划全部闹钟
    fun rescheduleAll()                               // 重启/时区自愈
}

interface ReminderChannel {
    fun trigger(meal: Meal, phase: MealPhase)         // V1: NotificationReminderChannel（通知+轻短震动）
}
```

| 基建组件 | 契约/职责 | 归属层 |
|----------|-----------|--------|
| `Fast16App` | `startKoin` 装配 3 个 module（Manifest `android:name` 挂载 M0 落地时补） | app |
| `AppDatabase` | Room `@Database`（3 实体 + 3 DAO，`schemaVersion=1`） | data |
| `SettingsStore` | DataStore 封装：Flow 读 + suspend 写（§1.5 偏好清单） | data |
| `PlanRepository` / `CheckInRepository` | 接口 + 本地实现 | data/domain |
| `PlanScheduler` + `AlarmPlanScheduler` | 排程/撤销/重排 | core |
| `ReminderChannel` + `NotificationReminderChannel` | 通知 + 轻短震动（文案取 `SpeechCatalog`） | core |
| `AppModule` / `DataModule` / `SchedulingModule` | Koin DSL 装配 | core |

### 1.4 Manifest 要点

- `POST_NOTIFICATIONS`、`SCHEDULE_EXACT_ALARM`、`RECEIVE_BOOT_COMPLETED`、`VIBRATE`（V1 轻短震动）
- `<receiver>`：`AlarmReceiver` / `BootReceiver` / `TimeChangedReceiver` / `GlanceAppWidgetReceiver`
- `theme`：`PixelTheme`（无 ActionBar，Compose 接管）

---

## 2. UI 骨架（Skeleton）

### 2.1 设计令牌（PixelTokens 定值）

**颜色**（`PixelColors`）：

| Token | 值 | Token | 值 |
|-------|-----|-------|-----|
| `bg` | `#0f0f1a` | `panel` | `#1a1c2c` |
| `green` | `#38b764` | `yellow` | `#ffcd75` |
| `red` | `#b13e53` | `blue` | `#41a6f6` |
| `white` | `#f4f4f4` | `gray` | `#7b7b8c` |

**字体**（`PixelType`）：数字/英文/计时用 `Press Start 2P`（字号阶梯 12/16/24/32）；中文标题用 ZCOOL 系列或得意黑；正文用系统黑体。

**间距**（8px 网格）：`xs=4 / sm=8 / md=12 / lg=16 / xl=24 / xxl=32`

**形状**（`PixelShape`）：像素阶梯圆角（`cornerSteps=3`，即 3 个 2px 台阶）+ 2px 黑描边。

**动效**（`PixelMotion`）：步进帧 `120ms/帧`；闪烁 `2 帧 ~400ms 循环`；弹窗/页面过渡 `3–4 帧 ~150ms`（出场 `1–2 帧 ~80ms`）。
> 场景规格与手法以 [`pixel-motion-guidelines.md`](pixel-motion-guidelines.md) 为准，本文为 token 定值来源；冲突时先改该文档再回写此处。

> 落地：M3 `ColorScheme` 由 `PixelColors` 覆写；业务组件只引用 tokens，不引用 M3 默认值。

### 2.2 路由表与导航图

```kotlin
// 类型安全路由（kotlinx.serialization）
@Serializable object HomeRoute        // 起始
@Serializable object CreateRoute      // 全屏（隐藏底栏）
@Serializable object RecordRoute      // 记录 = 统计概览 + 日历（合并页）
@Serializable object SettingsRoute
```

```
NavHost(start = HomeRoute)
 ├─ HomeRoute       ┐
 ├─ RecordRoute     ├─ 底部导航 Shell（3 Tab）
 ├─ SettingsRoute   ┘
 └─ CreateRoute     （全屏，无底栏）

Onboarding：不作为路由，由根层 overlay 触发（onboarding_seen==false）
```

- 底部导航 3 Tab：**首页 / 记录 / 设置**（像素图标 + 选中黄描边）。
- **RecordRoute = 日历 + 统计合并页**（UI 原型 v0.1 定稿）：顶部统计概览（连续打卡天数、本月/本周完成率、近 7 天柱状图）+ 下方月历（每日三餐状态点，点日期补打卡）。
- `Create` 从 Home 的「新建计划」按钮 push；生成成功 `popBackStack` 回 Home。

### 2.3 页面壳（Screen 清单 · MVI）

| Screen | Intent（sealed） | State（sealed UiState） | Reducer（纯函数） | M0 占位内容 |
|--------|------------------|------------------------|-------------------|-------------|
| `HomeScreen` | `HomeIntent`（新建计划 / 打卡 / 状态演示） | `Content(plan, character, fasting)` | `HomeReducer` | 空态引导「记录早餐」 |
| `CreateScreen` | `CreateIntent`（调时间 / 选候选 / 调备餐 / 生成） | `Content(step, candidates, sel, prep)` | `CreateReducer` | 两步流程占位 |
| `RecordScreen` | `RecordIntent`（切月 / 点日期 / 补打卡） | `Content(streak, rates, month, checkins)` | `RecordReducer` | 统计概览 + 空月历 |
| `SettingsScreen` | `SettingsIntent`（改偏好 / 重看说明） | `Content(settings)` | `SettingsReducer` | 设置项列表 |
| `OnboardingDialog` | 无 VM（由根层 State 控制显隐） | — | — | 8:16 说明弹窗 |

> **MVI 约定**：每个 Screen 由 `XxxIntent → XxxReducer → StateFlow<XxxState>` 驱动；Reducer 为**纯函数**（无 IO/时间源），单测覆盖全部 Intent；
> 写库 / 排程 / 通知+轻震等副作用在 ViewModel 内调 UseCase，一次性事件（跳转/Toast）走 `Channel`。详见设计方案 §5.2。
> UI 统一：`PixelTheme { Scaffold { ... } }`，`collectAsStateWithLifecycle` 订阅 State。

**角色资产基线（UI 原型 v0.1 定稿）**：Q 版人形 16×20，蓝色运动服（`#41a6f6`）+ 黄纽扣；肤色/发色为角色专属色（非 UI 令牌）；
待机动效 = 自然眨眼（约 3.1s 闭 1 帧）+ 呼吸起伏（约 1.3s 沉 1px）；备餐/进食/休息配道具帧动画（锅/碗筷/星星）；
角色渲染 `FilterQuality.none` + 整数倍缩放。

### 2.4 共享组件（Pixel* 家族，M0 实现清单）

| 阶段 | 组件 |
|------|------|
| **M0 必须** | `PixelTheme` + `PixelColors/PixelType/PixelShape/PixelMotion`、`PixelButton`、`PixelCard`、`PixelText`、`PixelDialog`、`PixelProgressBar` |
| M1 | `PixelNumber`、`PixelFrameAnimation`、`PixelBubble`、`PixelToast`、`PixelTimePicker`、`PixelSlider` |
| M2 | `PixelCalendar`、`PixelBars`、`PixelSwitch` |

### 2.5 主题与暗色

- `PixelTheme` 内置深色 `ColorScheme`（唯一，V1 不做浅色）。
- 全局 `statusBar` 颜色 = `bg`，`navigationBar` = `panel`。

---

## 3. 已定「定名」项

| # | 项 | 定值 |
|---|----|------|
| 1 | applicationId | `com.ly.fast16` |
| 2 | 应用英文名 | **Fast16**（`Fast16App`） |
| 3 | 应用显示名 | 「**816 轻断食**」（无冒号） |
| 4 | targetSdk | **37** |

> 命名已定，M0 可直接按本文件 `gradle init` 起工程。
