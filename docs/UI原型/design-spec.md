# Fast16 UI 原型设计规范（Design Spec）

> 目的：以接近 Figma 的精确度描述 `UI原型/index.html` 的整体设计、布局、组件、状态与交互动效。  
> 适用范围：Agent 还原 UI、实现 Compose 组件、评审视觉一致性时使用。  
> 源文件：`/Users/linyu/Documents/Obsidian Vault/Fast16/UI原型/index.html`

---

## 1. 画布与框架

| 项目 | 值 |
|---|---|
| 页面舞台 `.stage` | `max-width: 1080px`；`margin: 0 auto`；`display: flex`；`gap: 56px`；`align-items: flex-start` |
| 页面外框 `.page-head` | `max-width: 1080px`；`margin: 0 auto 24px` |
| 手机框 `.phone` | `flex: 0 0 432px` |
| 手机外框 `.phone-frame` | `width: 432px`；`background: #1a1a1a`；`border-radius: 44px`；`padding: 12px`；阴影 `0 0 0 2px #2a2a2a, 0 24px 70px rgba(0,0,0,.55)` |
| 手机屏幕 `.phone-screen` | `width: 408px`；`height: 884px`；`border-radius: 36px`；`overflow: hidden`；`background: #000` |
| 状态栏 `.statusbar` | `position: absolute; top: 0; left: 0; right: 0; height: 34px; z-index: 40; padding: 0 26px` |
| 挖孔 `.punch` | `top: 11px; left: 50%; width: 14px; height: 14px; border-radius: 50%; z-index: 41` |
| App 内容 `.app` | `position: absolute; inset: 34px 0 0; display: flex; flex-direction: column; background: var(--bg)` |
| 页面滚动区 `.screen-body` | `flex: 1; overflow-y: auto; padding: 14px 14px 76px` |
| 底部导航 `.app-tabs` | `position: absolute; left: 0; right: 0; bottom: 0; height: 58px; z-index: 30; display: flex; background: var(--panel); border-top: 2px solid var(--ink)` |
| 手势条 `.gestbar` | `position: absolute; bottom: 7px; left: 50%; width: 104px; height: 4px; border-radius: 99px; background: rgba(255,255,255,.45); z-index: 41` |
| Widget 预览区 `.widget-wrap` | `flex: 0 0 340px; padding-top: 8px` |
| Widget 卡片 `.widget` | `width: 320px; background: var(--bg); border: 2px solid #000; outline: 2px solid #3a3a4a; padding: 4px` |
| Widget 内层 `.widget-in` | `padding: 12px; border: 2px solid var(--ink); background: var(--panel)` |

---

## 2. 设计令牌

### 2.1 颜色

| Token | 值 | 用途 |
|---|---|---|
| `--bg` | `#0f0f1a` | 全局背景 |
| `--panel` | `#1a1c2c` | 卡片/面板底 |
| `--green` | `#38b764` | 成功、已打卡、进行中 |
| `--yellow` | `#ffcd75` | 待打卡、选中、提示 |
| `--red` | `#b13e53` | 警示、断食、错误 |
| `--blue` | `#41a6f6` | 信息、操作、链接 |
| `--white` | `#f4f4f4` | 正文、高亮 |
| `--gray` | `#7b7b8c` | 次要文字、禁用、无计划 |
| `--ink` | `#0f0f1a` | 描边/墨色 |

### 2.2 字体

| 用途 | 字体 |
|---|---|
| 数字/英文/计时 | `'Press Start 2P', 'DotGothic16', 'Courier New', monospace` |
| 中文正文/标题 | `'DotGothic16', 'Press Start 2P', 'PingFang SC', 'Microsoft YaHei', monospace` |
| 页面 `body` | 默认 `var(--cjk)`，字号 `13px` 为主，行高约 `1.8` |

原型中使用到的字号阶梯：

| 场景 | 字号 |
|---|---|
| Widget 标题/小标签 | 8–11px |
| Chip/小按钮 | 8–11px |
| 正文/说明 | 10–13px |
| 标题 `h1/.h1` | 13–18px |
| 断食倒计时 | 34px |
| 连续打卡数字 | 40px |

### 2.3 间距

| 名称 | 值 | 对应 class |
|---|---|---|
| `xs` | 4px | — |
| `sm` | 8px | `gap8`, `mt8` |
| `md` | 12px | `mt12` |
| `lg` | 16px | `mt16` |
| `xl` | 24px | — |
| `xxl` | 32px | — |

通用间距类：

```css
.mt8  { margin-top: 8px; }
.mt12 { margin-top: 12px; }
.mt16 { margin-top: 16px; }
.gap8 { gap: 8px; }
.row   { display: flex; align-items: center; gap: 10px; }
```

### 2.4 圆角与描边

- 像素阶梯圆角：`clip-path: polygon(...)`，实现 `cornerSteps≈3`，每台阶约 3px。
- 通用描边：`2px solid var(--ink)`。
- 手机外框圆角：`44px`；屏幕圆角：`36px`。
- Widget 外描边：`2px solid #000` + `outline: 2px solid #3a3a4a`。

### 2.5 Z-index 层级

| 层级 | 元素 |
|---|---|
| 30 | 底部导航 |
| 40 | 状态栏 |
| 41 | 挖孔、手势条 |
| 50 | Onboarding 遮罩 |
| 60 | Toast |

### 2.6 动效令牌

| 动效 | 值 |
|---|---|
| 角色帧间隔 | `260ms` |
| 待机呼吸 | 约每 `5` 帧（`1.3s`）沉 `1px` |
| 待机眨眼 | 约每 `12` 帧（`3.1s`）闭 `1` 帧 |
| Toast 显示时长 | `1.6s` |
| Toast 过渡 | `all .25s` |
| 演示震动 | `navigator.vibrate(80)` |

---

## 3. 组件规格

### 3.1 面板 `.panel` / `.panel-in`

| 属性 | 值 |
|---|---|
| `.panel` | `clip-path` 像素圆角；`background: var(--ink)`；`padding: 3px` |
| `.panel-in` | 同像素圆角；`background: var(--panel)`；`padding: 12px` |

### 3.2 按钮 `.pbtn`

| 状态/变体 | 样式 |
|---|---|
| 默认 `> span` | `display: block; padding: 9px 14px; font-size: 12px; background: var(--yellow); color: var(--ink)` |
| 绿色 `.green` | `background: var(--green); color: var(--ink)` |
| 蓝色 `.blue` | `background: var(--blue); color: var(--ink)` |
| 幽灵 `.ghost` | `background: var(--panel); color: var(--white); border: 2px solid var(--ink)` |
| 小号 `.sm` | `padding: 6px 10px; font-size: 11px` |
| 按下 `:active > span` | `transform: translateY(2px)` |

### 3.3 Chip `.chip`

| 属性 | 值 |
|---|---|
| 默认 | `font-family: var(--px); font-size: 8px; padding: 5px 8px; color: var(--white); background: var(--panel); border: 2px solid var(--ink); cursor: pointer` |
| 选中 `.on` | `color: var(--ink); background: var(--yellow)` |

### 3.4 分块进度条 `.seg`

| 属性 | 值 |
|---|---|
| 容器 | `display: flex; gap: 3px` |
| 格 `i` | `width: 9px; height: 16px; background: #23263a` |
| 点亮 `.on` | `background: var(--green)` |
| 警示 `.warn` | `background: var(--yellow)` |
| 关闭 `.off` | `background: var(--red)` |

### 3.5 三餐行 `.meal-row`

| 属性 | 值 |
|---|---|
| 布局 | `display: flex; align-items: center; gap: 10px; padding: 10px; background: var(--panel); border: 2px solid var(--ink)` |
| 行间距 | 相邻行 `margin-top: 8px` |
| 图标 | `width: 34px; height: 34px` |
| 餐名 | `font-family: var(--px); font-size: 10px; color: var(--white)` |
| 时间 | `font-family: var(--px); font-size: 12px; color: var(--yellow); margin-top: 3px` |
| 备餐 | `font-size: 9px; color: var(--gray); margin-top: 2px` |
| 状态点 `.meal-dot` | `width: 16px; height: 16px; border: 2px solid var(--ink)` |
| 状态点颜色 | `dot-y`: yellow；`dot-g`: green；`dot-n`: `#23263a` |

### 3.6 候选卡 `.cand`

| 属性 | 值 |
|---|---|
| 默认 | `border: 2px solid var(--ink); background: var(--panel); padding: 10px; cursor: pointer; width: 100%; text-align: left` |
| 卡片间距 | 相邻 `margin-top: 8px` |
| 选中 `.on` | `border-color: var(--yellow)` |
| 方案名 | `font-family: var(--px); font-size: 9px; color: var(--white)` |
| 时间 | `font-family: var(--px); font-size: 11px; color: var(--yellow); margin-top: 5px` |

### 3.7 步进器 `.stepper`

| 属性 | 值 |
|---|---|
| 容器 | `display: flex; align-items: center; gap: 8px` |
| 按钮 | `width: 30px; height: 30px; font-family: var(--px); font-size: 12px; background: var(--panel); border: 2px solid var(--ink); color: var(--white); cursor: pointer` |
| 数值 | `font-family: var(--px); font-size: 12px; color: var(--yellow); min-width: 44px; text-align: center` |

### 3.8 设置行 `.set-row`

| 属性 | 值 |
|---|---|
| 默认 | `display: flex; align-items: center; justify-content: space-between; padding: 12px; background: var(--panel); border: 2px solid var(--ink); cursor: pointer` |
| 行间距 | 相邻 `margin-top: 8px` |
| 名称 | `font-size: 12px; color: var(--white)` |
| 值 | `font-family: var(--px); font-size: 9px; color: var(--gray)` |

### 3.9 日历 `.cal-grid` / `.cal-cell`

| 属性 | 值 |
|---|---|
| 星期表头 `.cal-wd` | `font-family: var(--px); font-size: 7px; color: var(--gray); text-align: center; padding: 4px 0` |
| 网格 | `display: grid; grid-template-columns: repeat(7, 1fr); gap: 3px; margin-top: 10px` |
| 格子 `.cal-cell` | `border: 2px solid var(--ink); background: var(--panel); padding: 4px 0 5px; text-align: center; cursor: pointer` |
| 非本月 `.other` | `opacity: .35` |
| 今天 `.today` | `outline: 2px solid var(--yellow)` |
| 日期数字 | `font-family: var(--px); font-size: 9px; color: var(--white)` |
| 状态点 `.cal-dots` | `display: flex; justify-content: center; gap: 2px; margin-top: 4px` |
| 状态点尺寸 | `width: 4px; height: 4px` |

### 3.10 Toast `.toast`

| 属性 | 值 |
|---|---|
| 位置 | `position: absolute; left: 50%; bottom: 76px; transform: translateX(-50%); z-index: 60` |
| 默认 | `opacity: 0; pointer-events: none; transition: all .25s` |
| 显示 `.show` | `opacity: 1; transform: translateX(-50%) translateY(0)` |
| 样式 | `background: var(--ink); border: 2px solid var(--yellow); color: var(--yellow); font-size: 11px; padding: 8px 14px` |

### 3.11 Onboarding 弹窗

| 属性 | 值 |
|---|---|
| 遮罩 `.overlay` | `position: absolute; inset: 0; z-index: 50; display: none; align-items: center; justify-content: center; background: rgba(5,5,10,.86)` |
| 显示 `.show` | `display: flex` |
| 弹窗 `.dialog` | `width: 88%` |
| 标题 `.dlg-title` | `font-family: var(--px); font-size: 13px; color: var(--yellow); text-align: center; margin-bottom: 12px` |
| 正文 `.dlg-body` | `font-size: 12px; color: var(--white); line-height: 2` |
| 高亮 `.hl` | `color: var(--yellow)` |
| 主按钮 | 全宽，`margin-top: 16px` |

### 3.12 气泡 `.bubble`

| 属性 | 值 |
|---|---|
| 位置 | `position: relative; margin: 10px auto 0; max-width: 250px` |
| 样式 | `background: var(--white); color: var(--ink); padding: 8px 12px; font-size: 12px; line-height: 1.6; text-align: center` |
| 三角尾巴 | `::after`，`border-top: 8px solid var(--white)` |

---

## 4. 页面布局顺序

### 4.1 Home

```
TODAY + 日期摘要 + streak chip
hr
角色舞台（130×152）
气泡
断食计时面板（mt12）
三餐列表（mt16）
角色状态演示 chips（mt16）
```

### 4.2 Record

```
RECORD 标题 + 副标题
hr
统计概览面板
  连续打卡 / 本月完成率 / 本周完成率 / 近7天柱状图
日历头部（◀ 月份 ▶）
日历网格
日期详情面板（点击日期后显示）
```

### 4.3 Settings

```
SETTINGS 标题 + 副标题
hr
7 个设置行
```

### 4.4 Create

```
第 1 步：
  NEW PLAN 标题 + 取消
  hr
  说明
  早餐时间步进面板
  自动打卡早餐开关
  下一步按钮

第 2 步：
  说明
  3 个候选卡
  午餐/晚餐备餐微调面板
  生成就餐计划按钮
```

---

## 5. 状态与交互规范

### 5.1 首页状态

| 条件 | 角色 | 时间轴/列表 |
|---|---|---|
| 今日无计划 | `idle` | 空态卡 + 新建计划按钮 |
| 0 餐完成 | `fasting` | 显示三餐待打卡 |
| 部分完成 | `eat` | 已打卡绿点，其余黄/灰 |
| 全部完成 | `rest` | 全绿点 |

### 5.2 打卡交互

- 首页三餐按钮：点击切换 `D.checkins`，Toast 提示。
- 记录页日历补打卡：点击日期打开详情，点击餐行按钮切换状态，日历同步重绘。
- Widget：点击任意三餐图标只切换午餐演示状态；`打卡` 按钮 Toast「小组件：午 已打卡（演示）」。

### 5.3 新建计划交互

- 步进器：±5 分钟，早餐范围 `00:00–23:55`，备餐范围 `0–180`。
- 候选卡：点击选中，黄色描边。
- 生成：写演示 `D.plan`，早餐自动打卡取决于开关，返回首页并 Toast。

### 5.4 Onboarding

- 启动后约 `600ms` 自动显示。
- 关闭：点击「知道了，开始使用」移除遮罩，Toast「已记住，不再自动弹出」。
- 可通过设置页「查看 8:16 说明」再次打开。

---

## 6. 像素角色规格

| 项 | 值 |
|---|---|
| 画布 | 16×20 像素 |
| 渲染 | Canvas 逐像素绘制，CSS `image-rendering: pixelated` |
| 缩放 | 整数倍（原型常用 4 倍：64×80px） |
| 颜色 | 蓝运动服 `#41a6f6`；黄纽扣 `#ffcd75`；肤色 `#f2cfa0`；发色 `#4a3628`；描边 `#0f0f1a` |
| 状态 | `idle`、`fasting`、`prep`、`eat`、`rest` |
| 帧率 | 原型角色动画 `260ms/帧` |
| 待机 | 呼吸约 `1.3s` 沉 1px；眨眼约 `3.1s` 闭 1 帧 |
| 备餐道具 | 锅 + 蒸汽 |
| 进食道具 | 碗 + 筷子 |
| 休息道具 | 星星/闪光 |

---

## 7. 与正式实现的映射

| 原型元素 | 正式实现建议 |
|---|---|
| `.panel` / `.panel-in` | `PixelCard` |
| `.pbtn` | `PixelButton` |
| `.chip` | `PixelChip` 或 `FilterChip` 的像素化封装 |
| `.seg` | `PixelProgressBar` |
| `.meal-row` | `PixelTimeAxis` 或 `MealRow` |
| `.cand` | `CandidateCard` |
| `.stepper` | `PixelStepper` |
| `.cal-grid` | `PixelCalendar` |
| `.toast` | `PixelToast` |
| `.overlay` / `.dialog` | `PixelDialog` |
| `.bubble` | `PixelBubble` |
| 角色 Canvas | `PixelSprite` + `PixelFrameAnimation` |
| Widget | Glance 静态像素卡片 |

---

> 本文件用于精确描述原型设计；若与 `index.html` 有冲突，以 `index.html` 实际 CSS/结构为准。
