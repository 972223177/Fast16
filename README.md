# Fast16 · 816 轻断食

一款 Android 本地 App「816 轻断食」，用于 8:16 间歇性断食的用餐排程与提醒。

## 核心玩法

记录早餐时间 → 自动生成午/晚候选方案 → 用户选取/微调 → 生成当日计划 → 到点提醒「备餐 / 开吃」→ 打卡记录与统计。

## 功能特性

- 8:16 间歇性断食用餐计划生成与微调
- 备餐 / 开吃到点提醒（通知 + 震动）
- 打卡记录与统计（含日历视图）
- 桌面 Widget（Glance）
- 深色像素风 UI，纯本地离线，无账号、无后端、无数据上传

## 技术栈

| 项目 | 说明 |
|------|------|
| 语言 | Kotlin 2.2 |
| UI | Jetpack Compose + Material3 |
| 架构 | MVI（Intent → Reducer → StateFlow）、Feature-first 单模块 |
| 数据 | Room 3.0 + DataStore Preferences |
| 依赖注入 | Koin 4.x |
| 导航 | Navigation Compose |
| Widget | Glance |
| 最低系统 | Android 7.0（minSdk 24），targetSdk 37 |
| 应用 ID | `com.ly.fast16` |

## 构建

```bash
./gradlew :app:assembleDebug        # 构建 debug APK
./gradlew :app:testDebugUnitTest    # 单元测试
./gradlew :app:lintDebug            # lint
./gradlew :app:assembleRelease      # release APK（全量 R8 + lintVitalRelease；签名配置见 local.properties）
```

## 著作权与许可证

**Copyright © 2026 linyu**

本软件按 **MIT License** 开源，详见 [LICENSE](./LICENSE)。

### 软件著作权登记信息

- **软件名称**：816轻断食（Fast16）饮食管理软件
- **软件版本**：V1.0
- **著作权人**：linyu
- **开发完成日期**：2026-09-01
- **首次发表日期**：未发表（登记后可补充）
- **权利取得方式**：原始取得（独立开发）

> 注：上述软著信息供登记申请参考，最终以《计算机软件著作权登记申请表》填报内容为准。

### 合规文本维护

隐私政策 / 用户协议的**唯一事实源**是 `app/src/main/assets/*.txt`（App 内弹窗展示）；
商店后台要求的公开 URL 页面 `docs/*.html` 由脚本从 txt 生成，二者内容保持一致：

```bash
./scripts/sync-legal.sh
```

修改文案只需改 txt，然后运行脚本重新生成 HTML 即可。

## 致谢

- [Press Start 2P](https://github.com/google/fonts) — SIL OFL 1.1
- [Fusion Pixel](https://github.com/TakWolf/fusion-pixel-font) — SIL OFL 1.1

内置字体授权明细见 [`docs/font-licenses.md`](docs/font-licenses.md)。
