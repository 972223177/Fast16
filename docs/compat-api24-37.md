# Android API 24–37 兼容性避坑清单（权限与行为变更）

> 依据 Android 官方 Behavior Changes 文档（33–37 为 2026-08 抓取的官方页；24–32 为官方长期稳定的既有行为）整理，聚焦**权限收紧与本项目相关行为变更**。本项目 `minSdk 24 / targetSdk 37`（见 AGENTS.md §1）。
>
> **两层区分（读表前必懂）**：
> - 「运行时」= App **运行在** Android X 系统上，无论 targetSdk 多少都受影响；
> - 「targetSdk」= 只有 **targetSdk ≥ X** 才受影响。本项目 targetSdk 37，**所有版本的 targetSdk 层约束全部适用**；minSdk 24 的旧设备兼容一律走版本门控下沉 `core/`（AGENTS.md §6 规则）。
>
> 编码前通读 §5 红线清单；写权限/闹钟/通知相关代码时逐条核对本文。
>
> **安装阻断（targetSdk 底线）**：Android 14 起禁止安装 targetSdk < 23 的应用；Android 15 起禁止 < 24。targetSdk 必须随新版本及时更新，否则新设备无法安装。

## 0. 本项目涉及的权限与组件速查

| 权限/组件 | 版本门槛 | 本项目处置 |
|-----------|---------|-----------|
| `POST_NOTIFICATIONS`（运行时） | 33+ 运行时 | **必须运行时请求**（引导页/首次排程前），拒绝后提醒功能静默失效 |
| `SCHEDULE_EXACT_ALARM` | 31+ 声明；14 起**默认拒绝** | 必须 `canScheduleExactAlarms()` 检查 + 引导授权 + 非精确降级路径 |
| `USE_EXACT_ALARM` | 33+ | 仅限闹钟/日历类核心功能应用；本项目若走此权限则免用户授权，但注意商店政策 |
| `VIBRATE` | 普通权限 | 正常声明即可 |
| `RECEIVE_BOOT_COMPLETED` | 普通权限 | 正常声明；注意「受限」状态下收不到（§3 T 段） |
| NotificationChannel | 26+ 必须 | 任何通知前必须建 Channel，否则通知静默丢弃 |
| 前台服务 | 33 声明 type / 34 强制 | 本项目 V1 不用 FGS；若未来加入见 §3/§4 约束 |

---

## 1. Android 7–10（API 24–29）

| 版本 | 层级 | 变更 | 本项目影响 |
|------|------|------|-----------|
| 7.0 (24) | — | Doze 随身化（充电外均启用）；`setExactAndAllowWhileIdle` 窗口进一步收紧 | 排程必须用 `setExactAndAllowWhileIdle`，容忍少量漂移 |
| 8.0/8.1 (26/27) | 运行时 / targetSdk 26+ | **通知必须挂 Channel**（不建则丢弃）；**后台执行限制**（后台 Service 被停，所有应用）；隐式广播大量禁发（targetSdk 26+） | `AlarmReceiver`/`BootReceiver` 走 Manifest 注册（豁免隐式广播禁令）；不依赖任何后台 Service |
| 9 (28) | targetSdk 28+ | **明文 HTTP 默认禁止**（网络安全配置默认值）；非 SDK 接口限制（运行时，灰名单） | 本项目离线无网络；若未来加网络必须 HTTPS |
| 10 (29) | targetSdk 29+ | **分区存储**：`READ_EXTERNAL_STORAGE` 收紧、后台定位需单独声明 | 本项目不碰外部存储，仅 Room/DataStore；无影响 |

## 2. Android 11–12L（API 30–32）

| 版本 | 层级 | 变更 | 本项目影响 |
|------|------|------|-----------|
| 11 (30) | 运行时 | **包可见性过滤**：查其他应用需 `<queries>` 或 `QUERY_ALL_PACKAGES` | 提醒跳转系统设置等系统组件不受限；若需检测外部 App 需补 `<queries>` |
| 11 (30) | targetSdk 30+ | 分区存储强制执行 | 同上，无影响 |
| 12 (31) | targetSdk 31+ | **`PendingIntent` 必须显式指定 `FLAG_IMMUTABLE`/`FLAG_MUTABLE`**，否则抛异常 | 所有 `PendingIntent`（通知、闹钟）**一律加 FLAG_IMMUTABLE** |
| 12 (31) | targetSdk 31+ | **`SCHEDULE_EXACT_ALARM` 引入**（`setExact*` 需声明，用户可在设置撤回）；**通知 trampoline 禁止**（不得经 Service/BroadcastReceiver 中转启动 Activity）；蓝牙权限换 `BLUETOOTH_CONNECT/SCAN`（不再用定位）；**后台启动前台服务被禁**（少数豁免） | 通知用 `PendingIntent.getActivity` 直达；排程必须处理精确闹钟授权（见 §0）；不用 FGS |
| 12L (32) | — | 大屏任务栏等 UI 变化 | 无实质影响 |

## 3. Android 13–14（API 33–34）

| 版本 | 层级 | 变更 | 本项目影响 |
|------|------|------|-----------|
| 13 (33) | 运行时 | **`POST_NOTIFICATIONS` 运行时权限**（按 targetSdk 33+ 生效）；**预测返回 opt-in**（`enableOnBackInvokedCallback`）；**「受限」应用不收 `BOOT_COMPLETED`** | ① 通知权限运行时请求，**被拒后 FGS 通知也进不了抽屉**；② 被系统标记「受限」后重启自愈（BootReceiver）失效——属可接受降级，但需知晓 |
| 13 (33) | targetSdk 33+ | `READ_MEDIA_*` 细化媒体权限（替代 `READ_EXTERNAL_STORAGE`）；广告 ID 需声明 `AD_ID`；FGS 必须声明 type；`USE_EXACT_ALARM` 引入（限闹钟/日历类） | 见 §0；本项目不声明 FGS type |
| 14 (34) | 运行时 | **`SCHEDULE_EXACT_ALARM` 对新装应用（target 33+）默认拒绝**（闹钟/日历类豁免）；通知**可由用户取消**（滑动去除不失去功能）；`USE_FULL_SCREEN_INTENT` 政策收紧（仅通话/闹钟类保留） | 精确闹钟授权流程为**必答题**：`canScheduleExactAlarms()` false → 引导 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` 设置页 → 降级 `setInexactRepeating`；全屏提醒若使用需做 `canUseFullScreenIntent()` 检查 |
| 14 (34) | targetSdk 34+ | **每个 FGS 必须指定类型**（新增 shortService/specialUse 等）；**隐式 Intent 只能发给 exported 组件**；**动态注册 Receiver 必须指定 `RECEIVER_EXPORTED/NOT_EXPORTED`**（仅收系统广播可不指定）；**后台 Activity 启动再收紧**（PendingIntent 需 opt-in）；动态加载文件必须只读 | `TimeChangedReceiver` 等动态注册时注意 export 标志；不用隐式 Intent 启动自有组件 |

## 4. Android 15–17（API 35–37）

| 版本 | 层级 | 变更 | 本项目影响 |
|------|------|------|-----------|
| 15 (35) | targetSdk 35+ | **Edge-to-edge 强制**（状态/导航栏透明，`statusBarColor` 等弃用；cutout 模式强制 ALWAYS）；`BOOT_COMPLETED` **不得启动** dataSync/camera/mediaPlayback 等类型 FGS | Compose M3 组件自动处理 insets，自定义容器需用 insets API；不用 FGS（BootReceiver 只重排闹钟，合规） |
| 15 (35) | 运行时 | **16KB 页大小**（含 native 库的 App 需对齐）；**TLS 1.0/1.1 禁用**；**DND 全局态不可改**；Kotlin `removeFirst/removeLast`（OpenJDK 17 更新）在 minSdk≤34 抛 `NoSuchMethodError`，用 `removeAt()`；`dataSync`/`mediaProcessing` FGS 24h 内限 6h | 纯 Kotlin/无 NDK，16KB N/A（**若未来加 native 库必须对齐**）；`removeAt()` 写法进红线 |
| 16 (36) | targetSdk 36+ | **预测返回默认开启**：`onBackPressed` 不再回调、`KEYCODE_BACK` 不再分发；**sw≥600dp 大屏忽略 screenOrientation/aspectRatio 锁定**；`BODY_SENSORS` 被 Health 细粒度权限取代；Intent 过滤加固（选择启用） | 不用 `onBackPressed` 体系，走 Compose 预测返回；**竖屏锁定在平板/折叠屏失效**——布局必须自适应宽高比；无健康传感器 |
| 16 (36) | 运行时 | 本地网络保护（LNP，选择启用阶段） | 离线应用，无影响 |
| 17 (37) | targetSdk 37+ | **本地网络权限 `ACCESS_LOCAL_NETWORK` 强制**（访问 LAN 设备，16 起为 opt-in）；后台音频需前台服务 + WIU 权限、或精确闹钟权限 + `USAGE_ALARM` 音频流；大屏方向/宽高比限制**退出选项取消**（强制生效）；`static final` 反射修改抛 `IllegalAccessException`（JNI 直接崩溃）；BAL 限制扩展到 `IntentSender`；标准短信（非 WebOTP/Retriever 格式）收到的 OTP **3h 内不可用** | 大屏自适应从「建议」变「强制」；无网络/音频/反射/OTP 场景，其余 N/A；**闹钟权限反而成 17 上的合规优势**（后台音频豁免路径），佐证排程必须正确持有精确闹钟授权 |
| 17 (37) | 运行时 | `usesCleartextTraffic` 进入弃用计划（改用网络安全配置）；WebOTP 无权域名短信 3h 不可读；Keystore 密钥数上限（API 37+ 目标 5 万把）；隐式 URI 授权 18 起禁用（可先开 StrictMode 检测）；应用内存上限（App memory limiter） | 本项目离线 N/A；若未来加网络/OTP/安全相关功能需回看本行 |

## 5. 编码红线清单（Review 核对用）

- [ ] 通知：建 Channel（26+）→ 运行时请求 `POST_NOTIFICATIONS`（33+）→ 被拒走 UI 引导；不依赖「通知永远在」
- [ ] 闹钟：声明 `SCHEDULE_EXACT_ALARM`；每次排程前 `canScheduleExactAlarms()` 检查；未授权走设置引导 + `setInexactRepeating` 降级；评估 `USE_EXACT_ALARM`（核心功能为提醒的合规路径）
- [ ] `PendingIntent` 一律显式 `FLAG_IMMUTABLE`（31+ 强制）
- [ ] 通知点击直达 Activity（`getActivity`），禁止 trampoline（31+ 禁）
- [ ] 通知/闹钟相关不使用后台 Service/FGS；BootReceiver 仅做「重排闹钟」这一豁免安全动作
- [ ] 动态注册 Receiver 指定 export 标志（34+）；不隐式 Intent 启动未导出组件（34+）
- [ ] UI 走 edge-to-edge + insets（35+ 强制）；不做竖屏锁定假设（36+ 大屏忽略、37 强制）；返回导航用预测返回 API，不碰 `onBackPressed`（36+ 失效）
- [ ] 不用 `removeFirst()/removeLast()`（15 + minSdk≤34 崩溃）；`compileOptions` 与 kotlin 目标按工程配置
- [ ] 版本门控全部下沉 `core/`（AGENTS.md §6），feature 层零裸 `Build.VERSION` 判断
- [ ] 测试时用官方 compat 开关提前暴露问题：`adb shell am compat enable FGS_BOOT_COMPLETED_RESTRICTIONS <pkg>`、`RESTRICT_LOCAL_NETWORK <pkg>` 等

## 6. 参考来源

- https://developer.android.com/about/versions/17/behavior-changes-17 （targetSdk 37）
- https://developer.android.com/about/versions/16/behavior-changes-16 （targetSdk 36）
- https://developer.android.com/about/versions/15/behavior-changes-15 （targetSdk 35）
- https://developer.android.com/about/versions/14/behavior-changes-14 （targetSdk 34）
- https://developer.android.com/about/versions/13/behavior-changes-13 （targetSdk 33）
- 24–32 各版本 Behavior Changes 官方页（developer.android.com/about/versions/）
