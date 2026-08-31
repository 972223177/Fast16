# R8 与混淆规范（Fast16）

本文档是 R8 适配、keep 规则、新库引入与资源收缩的**权威规范**。修改构建配置、引入新依赖、或编写反射 / 序列化 / 枚举持久化 / Manifest 组件代码前，必须先读本文档。

## 0. 现状总览

- **全量 R8 已开启**：AGP 9 新 DSL `buildTypes.release.optimization.enable = true`（`app/build.gradle.kts`）。全量 = shrink（裁剪）+ obfuscate（混淆）+ optimize（优化），无官方"只 shrink 不混淆"子开关（AGP 9 已移除实验性 PostProcessing 块）。
- **keep 规则文件**：`app/src/main/keepRules/rules.keep`。AGP 9 会自动合并 `src/main/keepRules/*.keep` 传入 R8（规则增长后按分区拆分为多个 `.keep` 文件）。
- **release 签名**：正式 keystore 位于 `~/.keystores/fast16-release.jks`（RSA 2048，有效期 100,000 天，alias `fast16`），签名信息存 `local.properties`（已被 .gitignore 排除，**禁止**写入入库文件）。`build.gradle.kts` 在签名配置缺失时回退 debug 签名（CI 场景，不可上架）。
- **崩溃栈**：R8 已保留 `SourceFile, LineNumberTable`，配合 `app/build/outputs/mapping/release/mapping.txt` 解混淆。
- **收益**：release APK 约 1.6MB（未开资源收缩）。

## A. R8 适配规范

### A.1 敏感模式检查清单（新代码红线）

新增代码命中以下任一模式时，必须先评估并登记到本文件，必要时补 keep 规则：

| 模式 | 处理要求 |
|------|---------|
| 反射（`Class.forName` / `::class.java` / `getMethod` / `getDeclaredField`） | 必须补 keep 并注释理由（依赖方 + 不可删原因） |
| 新增 `@Serializable` 类（含 Navigation type-safe 路由） | 已由基线规则 `-keep @kotlinx.serialization.Serializable class com.ly.fast16.**` 覆盖，但仍需在 `usage.txt` 验证序列化器未被裁剪 |
| 枚举 `name` / `valueOf` / `ordinal` 用于持久化 / Intent / 进程边界 | 必须登记到 A.2 契约表 |
| 新 Manifest 注册组件（Activity/Receiver/Service/Provider） | AGP 自动 keep，无需手动规则；勿在代码里反射这些类名 |
| WebView + JS 接口 | 禁止裸奔，`-keepclassmembers` 保留 JS 接口类；本项目当前无 WebView |
| JNI / so 库 | 禁止裸奔，登记 so 库路径与 keep；本项目当前无 so |
| `ServiceLoader` / `META-INF/services` | 需 keep 服务实现类；本项目当前无 |

### A.2 枚举字符串契约登记表

R8 full mode 会折叠 / 重命名枚举，以下枚举因依赖 `name` / `valueOf` / `ordinal` 的**字符串或序号稳定性**而构成契约，已由基线规则 `-keep,allowshrinking enum com.ly.fast16.domain.model.**` 覆盖：

| 枚举 | 值语义 | 契约用途 | 消费方 |
|------|--------|---------|--------|
| `MealPhase` | 无 value，纯枚举 | `name` 写 PendingIntent extra，`valueOf` 还原（跨进程） | `PlanScheduler.kt:84`、`AlarmReceiver.kt:40` |
| `ReminderMode` | Int（0-4） | `name` 写 DataStore，`valueOf` 读回 | `SettingsStore.kt:59,82`、`SettingsScreen.kt:87`（name 显示） |
| `PlanStatus` / `MealType` / `MealStatus` | Int（显式 value） | Room 列**存 Int**（`@ColumnTypeConverter`），`from(value)` 还原 | `Converters.kt`、`Entities.kt` |
| `CharacterState` | 纯派生，不落库 | 无持久化契约，keep 仅为防御 | 领域派生（`domain/schedule`） |

> **注意**：`AlarmRequestCodes`（`PlanScheduler.kt`）依赖 `MealType.ordinal` = **声明顺序**。变更枚举声明顺序会破坏已排程闹钟的 requestCode 契约，**变更顺序必须评估迁移**。

> **Room 枚举存储约定**：本项目枚举统一**存 Int**（`data/local/Converters.kt` 的 `@ColumnTypeConverter` + 枚举显式 `value` 字段），**不使用** Room 默认 String(name) 存储。DAO 传 Int 参数（如 `MealDao.updateStatus(status: Int)`）与 Int 列直接匹配，**不要改成传枚举**。

### A.3 keep 规则书写规范

- 单文件起步（`rules.keep`）+ **分区注释**（崩溃栈 / 序列化 / 枚举 / 依赖库），规则增长后按分区拆 `.keep` 文件。
- **每条 keep 必须附理由注释**：依赖方（哪个库 / 哪个代码路径）+ 不可删除原因。
- 优先 `-keep,allowshrinking`（保留但允许确认无用后移除）；确需整体保留用 `-keep`。
- **禁止在 keep 中写全局关闭选项** `-dontoptimize` / `-dontobfuscate`（AGP 9 默认禁止 consumer rules 含此类全局选项；应用自身 keepRules 允许但会损失收益，需评估）。
- 修改后必须跑 `assembleRelease` 并审计 `usage.txt` 闭环。

### A.4 release 门禁

- 任何涉及构建 / 依赖 / R8 敏感代码的改动，`./gradlew :app:assembleRelease`（随跑 `lintVitalRelease`）必须通过。
- 审计 `app/build/outputs/mapping/release/`：`mapping.txt`（解混淆依据，**随版本保留**）、`seeds.txt`（keep 生效）、`usage.txt`（无意外裁剪）。发现序列化器 / 路由类 / 枚举 / Manifest 组件被删 → 回补 keep 重跑。
- 真机 / 模拟器冒烟：冷启动（Koin 注入）→ 路由导航 → Room 读写 → DataStore 读写 → 闹钟 → 通知。

### A.5 决策边界

- **本期不开资源收缩**（`shrinkResources` 二期评估，见 C）。
- 若未来想"只 shrink 不混淆"：AGP 9 无 DSL 子开关，只能在本应用 keepRules 写 `-dontobfuscate`（应用模块允许；**consumer rules 中禁止**，勿写进任何库依赖）。

## B. 新库引入规范

引入新依赖（或升级依赖版本）必须：

1. **引入前评估**：该库是否自带 consumer rules（R8 keep）、是否使用反射 / 注解处理器 / `ServiceLoader` / 序列化 / 动态加载。典型：
   - Compose / Navigation / Room / DataStore / Koin / kotlinx-serialization / WorkManager / Glance：自带 consumer rules，通常免手动 keep。
   - 反射重库（Gson / 部分 ORM / 埋点 SDK）：需按库文档补 keep。
2. **引入后门禁**：`./gradlew :app:assembleRelease` + 审计 `usage.txt`，确认无运行时反射类被裁剪；依赖版本升级同样执行。
3. **登记**：更新下方「依赖 R8 覆盖清单」。

### 依赖 R8 覆盖清单

| 库 | 版本 | consumer rules | 备注 / 验证日期 |
|----|------|----------------|----------------|
| compose-bom (ui/material3/foundation) | 2026.08.00 | 自带 | 2026-08-31 冒烟通过 |
| navigation-compose | 2.9.3 | 自带（序列化路由） | 2026-08-31 冒烟通过 |
| room3-runtime / room3-compiler | 3.0.1 | 自带 | KSP 生成；枚举走 Int TypeConverter |
| koin-android | 4.2.2 | 纯 Kotlin DSL 无反射 | 免 keep |
| kotlinx-serialization-json | 1.11.0 | 自带 | 基线规则兜底 `$$serializer` + `@Serializable` 类名 |
| datastore-preferences | 1.1.7 | 自带 | 无反射 |
| kotlinx-coroutines | 1.11.0 | 自带 | |
| glance-appwidget | 1.1.1 | 传递引入 work-runtime 2.7.1 | **必须 keep `androidx.work.**`**（见下方 A.6 历史教训） |
| desugar_jdk_libs | 2.1.5 | AGP 自动注入 | java.time 脱糖 |

### A.6 历史教训（WorkManager / Glance）

Glance 依赖未落地（widget 未实现）时，`androidx.startup.InitializationProvider` 仍会在启动时自动初始化 `WorkManagerInitializer` → 创建 `WorkDatabase`（Room 2.x）。R8 会裁剪"未被真实使用"的 WorkManager DAO（`WorkDatabase.rawWorkInfoDao()` 等），导致冷启动崩溃 `Failed to create an instance of androidx.work.impl.WorkDatabase`。

**处理**：`rules.keep` 保留整个 `androidx.work.**` 包。**当 Glance widget 真正落地（真实使用 WorkManager）后，可评估收敛此规则**（届时 R8 会因真实调用自然保留所需类）。

## C. 资源收缩规范（二期执行）

开启 `shrinkResources` 前必须：

1. **前置审计**：`getIdentifier` 动态资源引用、`res/raw` 与代码引用、通知图标等系统保留资源、`R` 引用穿透代码。
2. **`tools:keep` / `tools:discard`**：动态 / 必须保留资源用 `tools:keep` 显式声明；确认剔除的资源用 `tools:discard` 注明理由。
3. **未引用资源治理**：开启后核对 `resources.txt`（R8 输出），删除确认无引用的死资源。
4. **AGP 9 集成行为差异**：AGP 9 资源收缩已集成进 R8（`android.r8.optimizedResourceShrinking` 默认 true，R8 同时考虑类与资源），行为与旧版 `shrinkResources` 不同，需按新行为验证；本期保持关闭。

## 附录：2026-08-31 全量 R8 落地审计记录

- codegraph 语义审计：5 个 domain 枚举的 `name`/`valueOf`/`ordinal` 引用点全部核对，`@Serializable` 仅 `core/navigation/Routes.kt` 4 个 data object。
- seeds.txt 确认：4 个路由类 + `$cachedSerializer`、6 个枚举 + `$VALUES`/`$ENTRIES`、6 个 Manifest 组件全部保留。
- usage.txt 确认：仅裁剪 data class 未使用生成方法（`copy`/`componentN`/合成构造器），无业务类意外删除。
- 冒烟发现并修复 pre-existing bug：`CreateScreen.kt:526` `"%.2d"` 非法格式串（`IllegalFormatPrecisionException`，`%d` 不支持精度）→ 改 `%02d`。
- 冒烟发现 pre-existing 业务状态问题（非 R8）：创建页「生成就餐计划」按钮 `enabled = state.errors.isEmpty()`，当前约束校验未通过保持禁用；属业务逻辑范畴，与本规范无关。
- 签名：正式 keystore 已生成并配置，APK V2 签名指纹与 keystore 一致。
