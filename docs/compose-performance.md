# Jetpack Compose 性能避坑指南

> 依据 Android 官方文档（Performance / Stability 章节，2026-06 版）整理，结合本项目 MVI + Feature-first 约定。写作 Compose UI 时**必读**；Review 时按下文逐条核对。

## 0. 核心心智模型

Compose 的性能问题几乎都源于一个词：**不必要的重组（Recomposition）**。

- 重组范围（Recomposition Scope）= 单个非内联 `@Composable` 函数。内联函数（如 `Box`、`Row`、`Column`）不构成独立范围。
- Compose 判断「参数没变 → 跳过重组」的前提是参数**稳定（Stable）/ 不可变（Immutable）**。
- 优先级：**避免过早优化** —— 先用 Layout Inspector / Composition Tracing 定位真实热点，再按本文治理。

---

## 1. 稳定性（Stability）：immutable class

### 1.1 判定规则

| 类型 | Compose 编译器判定 |
|------|-------------------|
| `val` + 基元类型 / `String` | ✅ 稳定 |
| 含 `var` 属性的类 | ❌ 不稳定 |
| `List` / `Map` / `Set`（kotlin.collections） | ❌ **始终不稳定**，即使元素全稳定 |
| 外部模块 / 未启用 Compose 编译器的库类 | ❌ 不稳定 |
| `MutableState<T>` 属性 | ✅ 稳定（T 不稳定也不影响外层类） |

### 1.2 首选：写出真正不可变的类

- 数据类全部 `val`，且属性类型本身稳定（基元 / `String` / 已稳定的类）。
- 可变 UI 状态一律用 `mutableStateOf(...)`，不要裸 `var`。

```kotlin
@Immutable
data class Snack(val id: Long, val name: String)   // 全 val → 稳定

class SnackUiState(
    val snacks: List<Snack>,               // ❌ 仍是 List → 不稳定
    ...
)
```

### 1.3 集合不稳定的三种解法（任选其一）

| 方案 | 做法 | 备注 |
|------|------|------|
| **不可变集合**（首选） | `ImmutableList<T>` / `PersistentList`（`kotlinx.collections.immutable`，Alpha 阶段） | 类型级根治 |
| **Stability 配置文件** | `stability_config.conf` 写 `kotlin.collections.*`，`composeCompiler { stabilityConfigurationFile = ... }` | 适合管不到的第三方类 |
| **@Immutable Wrapper** | `@Immutable data class SnackCollection(val snacks: List<Snack>)` | 不引库时的兜底 |

注意：`@Immutable` / `@Stable` 只是「向编译器签合同」（类比 `!!`），注错会导致**该重组时不重组**（UI 不更新），比性能问题更隐蔽。**能靠真实不可变性解决的，优先不加注解**。

### 1.4 诊断手段

- Compose Compiler Metrics：`composeCompiler { metricsDestination = ...; reportsDestination = ... }`，看输出中 `restartable skippable` / `unstable class` 标记。
- Layout Inspector → Composition Counts，看哪些组件重组次数异常。

---

## 2. 状态提升（State Hoisting）

状态提升是 Compose 的默认模式，也是性能手段——**把状态上移到唯一的所有者，下层组件变成无状态、天然可跳过**：

```kotlin
// 无状态子组件：参数稳定即可跳过重组
@Composable
fun Counter(count: Int, onIncrement: () -> Unit) { ... }

// 有状态容器：状态唯一所有者
@Composable
fun CounterScreen(viewModel: CounterViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Counter(state.count, onIncrement = viewModel::increment)
}
```

要点：

- **状态下传、事件上传**（单向数据流），与本项目 MVI `Intent → Reducer → StateFlow` 一致。
- 频繁变化的**动画/滚动/输入类状态不要提升到 Screen 级**——提升越高，失效的重组范围越大。局部状态留在最近的父级，或直接用 lambda 延迟读取（见 §3）。
- **不可变快照 + 事件 lambda** 是最佳参数形态：`State`（data class）+ `onXxx: () -> Unit`。lambda 引用（`viewModel::increment`）比每次新建 `{ viewModel.increment() }` 更利于记忆化（strong skipping 下差异缩小，但仍是好习惯）。
- ViewModel 持有的 UI State 应是**不可变 data class**（§1），重组时靠相等性短路。

---

## 3. 延迟读取状态（Defer State Reads）

将状态读取下移/封装为 lambda，缩小失效范围：

```kotlin
// ❌ scroll.value 在 Screen 级读取 → 滚动每帧重组整个 Screen
Title(snack, scroll.value)

// ✅ 传 lambda → 只重组 Title
Title(snack) { scroll.value }

// ✅✅ 用 lambda 版修饰符 → 跳过组合，直接走布局阶段
Modifier.offset { IntOffset(0, scrollProvider()) }

// ✅✅✅ 绘制阶段读取 → 跳过组合 + 布局（如颜色动画）
Modifier.drawBehind { drawRect(color) }
```

**规则：把频繁变化的状态传给修饰符时，永远用 lambda 版本**（`offset {}`、`background {}`、`graphicsLayer {}`、`drawBehind {}`、`alpha {}` 等）。

---

## 4. derivedStateOf：高频状态 → 低频派生

状态变化频率远高于 UI 需要的更新频率时，用派生状态截断：

```kotlin
// ❌ firstVisibleItemIndex 每次滚动都变 → 每帧重组
val showButton = listState.firstVisibleItemIndex > 0

// ✅ 只在 0 ↔ >0 切换时重组
val showButton by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
```

适用：滚动位置阈值、表单合法性（由多个 State 派生一个 Boolean）等。纯计算、与 State 无关的派生用 `remember(key)` 即可，不要滥用 `derivedStateOf`。

---

## 5. remember：缓存昂贵计算

```kotlin
// ❌ 每次重组都排序
items(contacts.sortedWith(comparator)) { ... }

// ✅ 首次组合计算，key 变化才重算
val sorted = remember(contacts, comparator) { contacts.sortedWith(comparator) }
```

- 更好的做法：**把计算移出组合**——排序/过滤在 ViewModel 里做，UI 只收结果。
- `remember` 必须给**正确的 key**：漏 key 会读到过期值（正确性 bug），key 太宽（如传整个对象而非其 id）会频繁失效。

---

## 6. LazyColumn：项 key 必写

```kotlin
LazyColumn {
    items(notes, key = { it.id }) { note -> NoteRow(note) }   // ✅
}
```

- 无 key 时列表项移动 = 旧行删除 + 后续全部重建（全列表重组）。
- key 必须稳定且唯一（优先 DB 主键/id）；不要用 `indexOf` 或集合索引。
- 配合 `contentType`（如 `{ it.type }`）可提升异构列表复用率。
- ⚠️ 本项目 SSOT 铁律：LazyColumn 直接订阅 DAO `Flow`，item 数据类保持不可变（§1），key 用实体主键。

---

## 7. 避免向后写入（Backwards Write）

**在组合中读取状态之后又写入它** → 每帧重组死循环：

```kotlin
@Composable
fun Bad() {
    var count by remember { mutableIntStateOf(0) }
    Text("$count")
    count++   // ❌ 读取之后写入 → 无限重组
}
```

规则：**组合期间只读状态，写入只发生在事件回调 / Side Effect 中**（`onClick`、`LaunchedEffect` 等）。

---

## 8. Strong Skipping 模式

- Kotlin 2.0+ 的 Compose 编译器（本项目 Kotlin 2.2）默认启用 strong skipping：不稳定参数也可跳过（自动记忆化 lambda；`remember` 的不稳定对象 key 可省略，编译器自动处理）。
- 即使如此，**不稳定类在列表 diff（equals 比较）时仍可能整列表重组**；§1 的不可变类实践仍然有效，不要把 strong skipping 当免死金牌。

---

## 9. 其他

- **@Preview（见 AGENTS.md §6）**：Preview 即组件可独立渲染的证明，客观上促使组件无状态化、参数化——与本文所有优化方向一致。
- **副作用 API 归类**：一次性副作用用 `LaunchedEffect` / `DisposableEffect`；组合期间的写入严禁直接放在函数体（§7）。
- **不要过度优化**：静态、低频重组的组件无需追求 skippable；参数 `equals` 成本大于廉价重组时，跳过反而更慢。

---

## 参考来源

- https://developer.android.com/develop/ui/compose/performance/bestpractices
- https://developer.android.com/develop/ui/compose/performance/stability/fix
- https://developer.android.com/develop/ui/compose/performance/stability/diagnose
