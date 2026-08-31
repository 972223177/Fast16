# Kotlin Coroutine 避坑与最佳实践

> 依据官方文档（Android 协程最佳实践 2026-07 版、Kotlin 协程取消与超时 2026-07 版）整理，结合本项目「全量 Kotlin Coroutines、禁 Java 线程锁」（AGENTS.md §6）的约定。写并发/异步代码前必读。

## 0. 两条铁律（先记住）

1. **禁止 Java 线程原语**：`synchronized` / `ReentrantLock` / `wait()` / `Thread.sleep()` / 阻塞式 `latch.await()` / `future.get()` 全部禁用——阻塞承载线程会挂死 `Dispatchers.Default`（仅 CPU 核数个线程）上的其他子 Coroutine。替代：`Mutex` / `Semaphore` / `Channel` / `delay()`；legacy 阻塞调用必须包 `withContext(Dispatchers.IO)`。
2. **取消是协作式的**：`cancel()` 只是请求，协程在下一次挂起点或显式检查时才真正停止——写循环/计算密集代码必须主动配合（见 §3）。

---

## 1. 结构化并发与作用域

- **ViewModel 创建协程并暴露 `StateFlow`**，不暴露 suspend 业务函数（视图只订阅状态）；一次性事件走 `Channel`（本项目 MVI 约定一致）。
- **禁止 `GlobalScope`**：不受控、不可测、无法统一 context。需要比屏幕更长的生命周期时，注入外部 `CoroutineScope`（由 `Application` 级创建，Koin 提供，测试可替换）：

```kotlin
class ArticlesRepository(
    private val externalScope: CoroutineScope,   // Koin 注入，非 GlobalScope
) {
    suspend fun bookmarkArticle(article: Article) {
        externalScope.launch { dataSource.bookmarkArticle(article) }.join()
    }
}
```

- **作用域选择**：工作只与当前屏幕相关 → `coroutineScope {}`（随调用方取消）；应用存活期间相关 → 注入的 `externalScope`。
- **数据层接口**：一次性操作 → suspend 函数；持续数据 → `Flow`（与 SSOT 铁律一致，UI 订阅 DAO Flow）。

---

## 2. Dispatchers：注入 + main-safe

- **不硬编码 `Dispatchers`**，构造注入（默认参数兜底），测试替换为 TestDispatcher：

```kotlin
class NewsRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,   // Koin 注入
) {
    suspend fun fetchNews(): List<Article> = withContext(ioDispatcher) { ... }   // main-safe
}
```

- **挂起函数必须 main-safe**：谁做阻塞/重活谁负责 `withContext` 切走，调用方无需关心线程——ViewModel/UseCase 里不应出现调度器切换。
- `viewModelScope` 已绑定 Main；测试用 `Dispatchers.setMain(testDispatcher)` 替换（封装 `MainDispatcherRule`，见 testing-best-practices.md §3）。

---

## 3. 取消：主动配合

| 手段 | 用法 | 注意 |
|------|------|------|
| 挂起点 | `delay` / `await` / `channel.receive` 等 kotlinx 挂起函数自动检查取消 | 自定义挂起用 `suspendCancellableCoroutine`，**不要用 `suspendCoroutine`**（不响应取消） |
| `ensureActive()` | 循环/重计算前检查，取消时抛 `CancellationException` | CPU 密集循环每轮调用 |
| `yield()` | 让出线程 + 检查取消 | 长计算不挂起会饿死同线程其他协程 |
| `runInterruptible {}` | 包 JVM 阻塞 API（`sleep`、`BlockingQueue.take`），取消时转为线程中断 | 协程取消默认**不**中断线程 |
| `withTimeoutOrNull { }` | 超时自动取消，返回 `null`（优于抛异常的 `withTimeout`，方便降级/重试） | 项目排程兜底/缓存读取优先用 |

- **`finally` 清理**：取消时仍会执行；但其中的挂起调用会立即抛 `CancellationException`——需用 `withContext(NonCancellable) { ... }` 包裹。**`NonCancellable` 只允许配 `withContext`**，配 `launch`/`async` 会破坏结构化并发的父子关系。
- **Prompt cancellation**：被取消的协程恢复时即使结果已就绪也直接抛异常——「拿到资源 → 关闭资源」之间的清理必须放 `finally`，不能依赖正常流程。

---

## 4. 异常：CancellationException 必须重抛（最大坑）

```kotlin
// ❌ 吞掉 CancellationException → 取消传播断裂，协程「假装」继续运行
runCatching { repository.fetch() }
    .onFailure { e -> showError(e) }

// ✅ 重抛取消异常，只处理业务异常
runCatching { repository.fetch() }
    .onFailure { e ->
        if (e is CancellationException) throw e
        showError(e)
    }
```

- `catch (e: Exception)` / `runCatching` / Result 包装都会吞 `CancellationException`——**任何宽泛捕获的第一个动作是判断并重抛取消异常**。
- 优先捕获具体异常类型（`IOException`），不裸捕 `Exception`/`Throwable`。
- `async` 在结构化并发下异常**立即传播给父级**并取消兄弟协程（不是只在 `await()` 时抛）；要在失败时不拖垮兄弟任务，用 `supervisorScope {}`，此时异常由 `await()` 处理。
- `viewModelScope.launch` 里未捕获的异常会**崩溃应用**——根层统一兜底（`CoroutineExceptionHandler` 记日志）+ 业务层按需捕获，二者不互斥。

---

## 5. 不暴露可变类型

```kotlin
private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
val uiState: StateFlow<UiState> = _uiState      // ✅ 对外只读
val uiState = MutableStateFlow(...)             // ❌ 外部可改，状态来源失控
```

所有可变状态收敛在单一所有者类内（与状态提升原则一致）。

---

## 6. 测试

- `runTest {}` + 注入 `TestDispatcher`（`StandardTestDispatcher` 可控性强 / `UnconfinedTestDispatcher` 简单直接）。
- 所有 TestDispatcher **共享同一 `testScheduler`**，`runTest` 会等它们全部完成；虚拟时间自动跳过 `delay`。
- 被测代码不硬编码 Dispatchers 时以上才成立——§2 注入是前提。
- 详细规范见 [`testing-best-practices.md`](testing-best-practices.md) §3。

---

## 7. 编码红线清单（Review 核对）

- [ ] 全文件无 `synchronized` / `Thread.sleep` / Java 锁（AGENTS.md §6）
- [ ] 无 `GlobalScope`；跨屏生命周期用注入的 externalScope
- [ ] Dispatchers 构造注入，业务代码零硬编码
- [ ] 挂起函数 main-safe；ViewModel 无调度器切换
- [ ] 宽泛 catch / `runCatching` 均已重抛 `CancellationException`
- [ ] CPU 密集循环有 `ensureActive()`/`yield()`
- [ ] `finally` 中的挂起清理已包 `NonCancellable`（仅 withContext）
- [ ] 超时优先 `withTimeoutOrNull` + 降级路径
- [ ] 可变 StateFlow 均为 `private`，对外只暴露只读类型

## 8. 参考来源

- https://developer.android.com/kotlin/coroutines/coroutines-best-practices（Android 协程最佳实践）
- https://kotlinlang.org/docs/coroutines-cancellation.html（取消与超时）
- https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html（Dispatchers 与 context）
