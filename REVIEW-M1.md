---
phase: M1-core-closed-loop
reviewed: 2026-09-01T00:00:00Z
depth: standard
files_reviewed: 35
files_reviewed_list:
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/com/ly/fast16/Fast16App.kt
  - app/src/main/java/com/ly/fast16/MainActivity.kt
  - app/src/main/java/com/ly/fast16/core/character/SpeechCatalog.kt
  - app/src/main/java/com/ly/fast16/core/designsystem/component/PixelBubble.kt
  - app/src/main/java/com/ly/fast16/core/designsystem/component/PixelFrameAnimation.kt
  - app/src/main/java/com/ly/fast16/core/designsystem/component/PixelMealDots.kt
  - app/src/main/java/com/ly/fast16/core/designsystem/component/PixelNumber.kt
  - app/src/main/java/com/ly/fast16/core/designsystem/component/PixelSlider.kt
  - app/src/main/java/com/ly/fast16/core/designsystem/component/PixelTimePicker.kt
  - app/src/main/java/com/ly/fast16/core/designsystem/component/PixelToast.kt
  - app/src/main/java/com/ly/fast16/core/di/AppModules.kt
  - app/src/main/java/com/ly/fast16/core/notification/NotificationFactory.kt
  - app/src/main/java/com/ly/fast16/core/notification/VibratorCompat.kt
  - app/src/main/java/com/ly/fast16/core/receiver/AlarmReceiver.kt
  - app/src/main/java/com/ly/fast16/core/receiver/CheckInReceiver.kt
  - app/src/main/java/com/ly/fast16/core/scheduling/PlanScheduler.kt
  - app/src/main/java/com/ly/fast16/core/scheduling/ReminderChannel.kt
  - app/src/main/java/com/ly/fast16/core/system/ExactAlarmGate.kt
  - app/src/main/java/com/ly/fast16/core/system/NotificationPermission.kt
  - app/src/main/java/com/ly/fast16/data/local/Daos.kt
  - app/src/main/java/com/ly/fast16/data/repository/CheckInRepository.kt
  - app/src/main/java/com/ly/fast16/data/repository/PlanRepository.kt
  - app/src/main/java/com/ly/fast16/domain/repository/CheckInRepository.kt
  - app/src/main/java/com/ly/fast16/domain/repository/PlanRepository.kt
  - app/src/main/java/com/ly/fast16/domain/usecase/CheckInUseCase.kt
  - app/src/main/java/com/ly/fast16/feature/create/ui/CreateScreen.kt
  - app/src/main/java/com/ly/fast16/feature/home/ui/HomeScreen.kt
  - app/src/main/java/com/ly/fast16/feature/onboarding/ui/OnboardingDialog.kt
  - app/src/main/java/com/ly/fast16/feature/settings/ui/SettingsScreen.kt
  - app/src/test/java/com/ly/fast16/core/character/SpeechCatalogTest.kt
  - app/src/test/java/com/ly/fast16/core/scheduling/AlarmRequestCodesTest.kt
  - app/src/test/java/com/ly/fast16/core/system/ExactAlarmGateTest.kt
  - app/src/test/java/com/ly/fast16/domain/usecase/CheckInUseCaseTest.kt
  - app/src/test/java/com/ly/fast16/feature/create/ui/CreateReducerTest.kt
findings:
  critical: 2
  warning: 5
  info: 2
  total: 9
status: issues_found
---

# Phase M1: Code Review Report

**Reviewed:** 2026-09-01T00:00:00Z
**Depth:** standard
**Files Reviewed:** 35
**Status:** issues_found

## Summary

对 Fast16 M1 核心闭环业务层（Create 流程 / Home 今日流 / 调度层 / 权限兼容层 / SpeechCatalog / 引导 / Settings）做 standard 深度审查，含跨文件调用链（CreateViewModel→savePlan/schedule/checkIn、AlarmReceiver/CheckInReceiver→KoinComponent inject、HomeViewModel combine+secondTicker、NotificationFactory PendingIntent、AlarmRequestCodes 契约、ExactAlarmGate/VibratorCompat 版本门控）。

发现 **2 个 Critical**（会直接导致 M1 提醒/打卡闭环完全失效），5 个 Warning，2 个 Info。最严重的问题集中在「排程→闹钟触发→提醒」这条主链：Create 排程时用 `id=0` 的 Meal 注册闹钟，且两个 Receiver 注入的 `CoroutineDispatcher` 在 Koin 中根本未注册——两条致命缺陷叠加，使 M1 的到点提醒功能实际不可达。UI/动效/权限门控部分整体符合红线，但存在若干健壮性与隐私隐患。

> 说明：审查中 `BootReceiver`/`TimeChangedReceiver` 为 M0 占位（`rescheduleAll` TODO），属已声明的 M2 范围，不计 Critical。

## Critical Issues

### CR-01: AlarmReceiver/CheckInReceiver 注入的 `CoroutineDispatcher` 在 Koin 中未注册，到点触发即抛 KoinException，提醒/通知打卡不可达

**File:** `app/src/main/java/com/ly/fast16/core/receiver/AlarmReceiver.kt:27`（CheckInReceiver.kt:28 同理；AppModules.kt 缺绑定）

**Issue:**
`AlarmReceiver` 与 `CheckInReceiver` 均通过 `private val dispatcher: CoroutineDispatcher by inject()` 惰性解析调度器，但 `AppModules`（core/di/AppModules.kt）中**从未注册任何 `CoroutineDispatcher` 的 Koin 定义**——仅有内联 `Dispatchers.IO` 用于 `setQueryCoroutineContext`。Koin 无法实例化抽象 `CoroutineDispatcher`，`by inject()` 在 `onReceive` 首次访问（`CoroutineScope(SupervisorJob() + dispatcher)`）时抛出 `KoinException: No definition found`。

后果（M1 核心闭环直接断裂）：
- 闹钟到点 → `AlarmReceiver.onReceive` 主线程抛 KoinException → 提醒通知永不触发。
- 通知「打卡」action → `CheckInReceiver` 同样崩溃 → 通知打卡不可用。
- `goAsync()` 已在抛异常前调用，但 `pending.finish()` 位于未启动的 launch `finally` 内，永远不执行（直到系统 ~10s 超时回收），Receiver 生命周期泄漏。
- 该异常在 `onReceive` 内未捕获，会触发应用 CrashHandler。

**Fix:**
在 `dataModule` 或新增的 module 中提供单例绑定，并让两 Receiver 共用：
```kotlin
// core/di/AppModules.kt
single<CoroutineDispatcher> { Dispatchers.IO }  // Receiver 侧保持 main-safe 需谨慎
```
建议 Receiver 用 main 调度器执行轻量解析、IO 由 repository 内部 withContext 承担：
```kotlin
single<CoroutineDispatcher> { Dispatchers.Main.immediate }
```
同时将 `goAsync()` 的 `finish()` 提到构造 scope 的 try/finally 外层，保证注入失败也能 `finish()`：
```kotlin
val pending = goAsync()
try {
    val scope = CoroutineScope(SupervisorJob() + dispatcher)
    scope.launch { try { handle(...) } finally { pending.finish() } }
} catch (t: Throwable) {
    pending.finish()
}
```

### CR-02: Create 排程使用 `id=0` 的 Meal 注册闹钟，`AlarmReceiver` 反查 meal 永远为 null，M1 提醒链整体失效

**File:** `app/src/main/java/com/ly/fast16/feature/create/ui/CreateScreen.kt:255-261`

**Issue:**
`doGenerate()` 在内存中构造的 `meals`（`Meal(id=0L)` 默认值）传给 `planRepository.savePlan(plan, meals)` 后，**没有重新取回带真实 DB id 的 Meal**。随后用原始列表 `scheduler.schedule(plan.copy(id = planId), meals)` 注册闹钟，`AlarmRequestCodes.of` 只用 planId/type/phase（正确），但 Intent extra `AlarmReceiver.EXTRA_MEAL_ID = meal.id = 0`。

触发时 `AlarmReceiver.handle` → `planRepository.getMealById(0L)` → 真实 meal id ≥ 1 → 返回 null → `?: return` 直接丢弃。**到点提醒永不显示**，与 CR-01 叠加使 M1「到点提醒」完全不可达。

> 注：`LocalPlanRepository.savePlan` 只返回 `planId`，不返回 upsert 后的 meal id，是根因；`CreateReducerTest.generate_keepsStateForViewModelSideEffect` 只测 reducer 不测副作用链，未覆盖此路径，属测试盲区。

**Fix:**
生成后从 DB 重新读取带真实 id 的三餐再排程：
```kotlin
val planId = planRepository.savePlan(plan, meals)
planRepository.watchMealsByDate(s.today).first().takeIf { it.isNotEmpty() }?.let { saved ->
    scheduler.schedule(plan.copy(id = planId), saved)
} ?: return
```
或让 `PlanRepository` 增加 `suspend fun savePlanAndReturnMeals(plan, meals): Pair<Long, List<Meal>>`（在事务内返回 upsert 后的 meal），一次性拿到真实 id。并为 `doGenerate` 补行为级单元测试（fake repo 返回自增 id，断言 scheduler 收到 `id != 0` 的 meal）。

## Warnings

### WR-01: `bubbleText` 依赖 deriver 契约的脆弱写法，可能触发 collect 崩溃

**File:** `app/src/main/java/com/ly/fast16/feature/home/ui/HomeScreen.kt:195-203`

**Issue:**
`CharacterState.PREP` 分支 `meals.first { it.prepMinutes > 0 }`、`EATING` 分支 `meals.last()`。当前 `CharacterStateDeriver` 保证 PREP 存在 prep>0 的 meal、EATING 时 meals 非空，故不抛。但这是隐性契约：一旦 deriver 逻辑调整（例如 PREP 判定条件改动），`combine{}.collect{}` 内抛 `NoSuchElementException`，而 `viewModelScope.launch` 未捕获异常会**崩溃应用**（coroutines-best-practices §4）。

**Fix:** 改为安全取值并防御：
```kotlin
CharacterState.PREP -> meals.firstOrNull { it.prepMinutes > 0 }
    ?.let { SpeechCatalog.text(MealPhase.PREP, it.type) } ?: fallbackText
CharacterState.EATING -> meals.lastOrNull()
    ?.let { SpeechCatalog.text(MealPhase.EATING, it.type) } ?: fallbackText
```

### WR-02: 排程自愈 `rescheduleAll()` 为 TODO 空实现，但 Boot/TimeChanged Receiver 已注册且 `exported=false`——重启/改时后闹钟静默丢失

**File:** `app/src/main/java/com/ly/fast16/core/scheduling/PlanScheduler.kt:116-118`

**Issue:**
`AlarmPlanScheduler.rescheduleAll()` 仅 `// TODO(M2)`，`BootReceiver`/`TimeChangedReceiver` 均为占位空实现。系统重启或时间/时区变更后，所有已排程的精确闹钟不会重建——用户重启手机后 M1 的到点提醒全部静默失效。虽标注 M2 范围，但 Manifest 已声明 `RECEIVE_BOOT_COMPLETED` 并注册接收器，形成「声明了能力但行为缺失」的隐患。建议在 M1 明确标注「重启自愈不可用」的已知限制，或提前实现 `rescheduleAll`（从 DAO 读今日计划重排）。

**Fix:** M1 若不放宽，至少在代码/文档明确该降级；M2 实现时遍历当日有效计划调用 `schedule`。

### WR-03: Meal 状态 reconcile 前台仅内存派生、不落库，与 CheckInUseCase 的落库写法不一致，状态可能漂移

**File:** `app/src/main/java/com/ly/fast16/feature/home/ui/HomeScreen.kt:157-161`

**Issue:**
HomeScreen `derive` 用 `MealStateMachine.advance` 生成 `reconciled` 副本仅用于展示，**不写库**；而 `CheckInUseCase` 通过 `updateMealStatus` 将状态落库。两种途径对 `MealStatus` 的更新语义不一致：闹钟漏触发时 DB 中 status 滞留为 SCHEDULED，UI 却显示 PREPARING/EATING（纯内存），下次冷启动或其它页读取 DB 时状态回退。AGENTS.md §6「派生字段不落库」主张时间态派生，故此处方向正确，但缺少统一收敛（要么都在读取时派生、要么 reconcile 显式落库），建议明确单一事实源。

**Fix:** 统一采用「时间+打卡」全派生、status 不落库（保持 `reconciled` 展示），并从 `CheckInUseCase.linkMealCompleted/checkInMeal` 移除/弱化对 `updateMealStatus` 的依赖，避免双写不一致。

### WR-04: `doGenerate()` 无异常兜底，DB 写 / AlarmManager 排程失败会在 viewModelScope 内崩溃应用

**File:** `app/src/main/java/com/ly/fast16/feature/create/ui/CreateScreen.kt:243-267`

**Issue:**
`savePlan`（Room IO）、`scheduler.schedule`（`AlarmManager`，可能抛 `SecurityException`/`IllegalArgumentException`）、`checkInUseCase.checkIn` 连续执行，全程无 try/catch。任一抛非 `CancellationException` 异常，沿 `viewModelScope.launch` 冒泡即**崩溃应用**（coroutines-best-practices §4）。且若 `schedule` 抛异常，前面的 savePlan 已落库，用户看到计划却无提醒，状态不一致。

**Fix:** 用 `try/catch`（捕获具体异常，`CancellationException` 重抛）包裹副作用链，失败时向 `_events` 发失败事件或 `_uiState` 置错误，避免崩溃并给用户反馈。

### WR-05: `allowBackup="true"` + 模板化（空）备份规则，健康数据会随云备份上传，违背「本地优先/隐私」红线

**File:** `app/src/main/AndroidManifest.xml:12`（`android:allowBackup="true"`，`data_extraction_rules.xml`/`backup_rules.xml` 为空模板）

**Issue:**
默认全量备份下，Room 库 `fast16.db`（断食/打卡健康数据）与 DataStore 设置会经 Android Auto Backup 上传 Google 云。AGENTS.md §6「本地优先/隐私：V1 不联网、不上传」——健康数据默认云备份与该原则冲突，且模板文件自带 `TODO` 注释未被清理。

**Fix:** 在 `data_extraction_rules.xml`/`backup_rules.xml` 显式 `exclude` 掉数据库与 DataStore（或按隐私需求关闭 `allowBackup`），并清理模板 TODO 注释。

### WR-06: 代码/测试中残留 FQN 全限定引用，违反 AGENTS.md §6「禁 FQN」

**File:** `app/src/main/java/com/ly/fast16/feature/home/ui/HomeScreen.kt:277-279`（`com.ly.fast16.core.designsystem.component.MealDotState.*`）、`app/src/test/java/com/ly/fast16/feature/create/ui/CreateReducerTest.kt:52-53`（`com.ly.fast16.core.time.Time.timeOf`）

**Issue:**
按 AGENTS.md §6「代码规范（import 优先，禁 FQN）」，类名应 `import` 后短名引用。此处散落全限定类名，与工程规范相悖，降低可读性并可能引入 lint 噪音。

**Fix:** 顶部 `import com.ly.fast16.core.designsystem.component.MealDotState` / `com.ly.fast16.core.time.Time`，正文改用 `MealDotState.CHECKED_IN`、`Time.timeOf(...)`。

## Info

### IN-01: Receiver 内每次触发新建未持有作用域的 `CoroutineScope(SupervisorJob() + dispatcher)`

**File:** `app/src/main/java/com/ly/fast16/core/receiver/AlarmReceiver.kt:44`、`CheckInReceiver.kt:37`

**Issue:** 每次 `onReceive` 新建一个无生命周期管理的 scope（fire-and-forget），不保存、不取消。goAsync 语义下 `finally { pending.finish() }` 能兜底，但 scope 与 Job 无统一持有者。修复 CR-01 时建议一并收敛：scope 可成员持有并在进程存活期间复用，或明确注释其短生命周期合理性。

### IN-02: `OnboardingRoot` 内联 new `NotificationPermission` 且未处理 shouldShowRationale

**File:** `app/src/main/java/com/ly/fast16/feature/onboarding/ui/OnboardingDialog.kt:48-50`

**Issue:** 每次关闭引导都 `NotificationPermission(activity)` 新建实例并 `launchRequest`；对 API 33+ 已授权或曾拒绝的场景无 `isGranted`/`shouldShowRationale` 判断，可能重复弹系统权限框或与「拒绝后引导说明」策略不符。建议注入单例 `NotificationPermission`，并在请求前判断 `isGranted`。

## 测试质量观察

- `CreateReducerTest` 只覆盖 reducer 纯函数，**未覆盖 `doGenerate` 副作用链**（savePlan→schedule→checkIn），是 CR-02 漏网的直接原因；建议补 ViewModel 级行为测试。
- `AlarmRequestCodesTest` / `ExactAlarmGateTest` / `CheckInUseCaseTest` / `SpeechCatalogTest` 覆盖了契约与行为，质量良好；`CheckInUseCaseTest.uncheckIn_delegatesToRepository` 断言 `assertTrue(true)` 属无效断言（仅测不抛），可改断言 fake 状态。

---

_Reviewed: 2026-09-01T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
