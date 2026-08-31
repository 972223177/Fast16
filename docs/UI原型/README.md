# Fast16 UI 原型说明（Agent 可读版）

本文是从 `UI原型/index.html` 提取的结构化说明，供 Codex / CodeBuddy / DSH 等 Agent 在不解析 HTML 的情况下理解原型内容与交互。

> 原型源文件：`/Users/linyu/Documents/Obsidian Vault/Fast16/UI原型/index.html`  
> 性质：**纯 UI 交互预览**，非最终 Android 实现。

---

## 1. 原型总览

- 平台参照：Android Pixel 8 手机框架 + 右侧桌面 Widget 预览。
- 视觉风格：深色像素风，主色 `#0f0f1a` 背景、`#1a1c2c` 面板，主绿/黄/红/蓝作为状态色。
- 字体：`Press Start 2P`（数字/英文）+ `DotGothic16`（中文近似像素）；加载失败时回退等宽字体。
- 交互范围：首页、记录页、设置页、新建计划、引导弹窗、日历补打卡、Widget 打卡均可点击体验。

---

## 2. 页面结构

| 页面 | 路由/ID | 说明 |
|---|---|---|
| Home 今日首页 | `scr-home` | 角色 + 断食倒计时 + 三餐时间轴 + 新建/打卡 |
| Record 记录页 | `scr-record` | 统计概览 + 月历打卡（合并页） |
| Settings 设置页 | `scr-settings` | 偏好列表 + 查看 8:16 说明 |
| Create 新建计划 | `scr-create` | 全屏流程：早餐时间 → 选候选 → 微调 → 生成 |
| Onboarding 引导 | `overlay#onboarding` | 首启像素风 8:16 说明弹窗 |
| Widget 预览 | 左侧 `.widget` | 桌面小组件卡片预览 |

---

## 3. Home 今日首页

### 内容

- 顶部：`TODAY` 标题、日期/早餐摘要、连续打卡天数入口（🔥 12 天）。
- 角色区：像素小人「小健」+ 像素气泡。
- 断食计时：`断食中 · 距下一餐` + `03:20:12` 倒计时 + 16 格分块进度条。
- 三餐列表：早餐/午餐/晚餐，每行含：
  - 像素饭碗图标
  - 餐名
  - 时间
  - 备餐分钟
  - 状态点：`dot-g` 绿=已打卡、`dot-y` 黄=待打卡、`dot-n` 灰=无备餐（原型中早餐 prep=0 也会显示灰点）
  - 打卡按钮

### 状态

- 无计划：显示空态引导「今天还没有就餐计划」+「＋ 新建计划」。
- 有计划：根据打卡数派生角色状态：
  - 0 餐完成 → `fasting`
  - >0 但未全完成 → `eat`
  - 3 餐全完成 → `rest`

### 角色状态演示（原型专用）

首页底部有 5 个 chip：待机 / 断食 / 备餐 / 进食 / 休息，可手动切换角色动作。

---

## 4. Create 新建计划

两步流程：

### 第 1 步 · 早餐时间

- 说明：早餐时间是进食窗口起点；界面文案写“默认取当前时间”，但原型实际固定演示值为 `08:00`。
- 控件：`-` / `+` 步进器，步长 5 分钟，范围 00:00–23:55。
- 开关：自动打卡早餐（默认开，记录即已吃）。
- 按钮：`下一步：选午/晚餐 ▶`

### 第 2 步 · 选方案 + 微调

- 系统按 `B`（早餐分钟）、`W=480`、`minGap=180`、`bufferEnd=30` 生成 3 套候选：
  - **均衡**：`lunch = B + W/2`，`dinner = min(B+W-be, lunch+minGap+L/2)`
  - **午餐偏早**：`lunch = B + minGap + 0.25L`，`dinner = lunch + minGap`
  - **晚餐偏晚**：`lunch = B + minGap + 0.75L`，`dinner = B + W - bufferEnd`
  - 其中 `L = W - bufferEnd - minGap`
- 候选卡片显示：方案名 + `午 xx:xx · 晚 xx:xx`，点击选中（黄色描边）。
- 微调：午餐备餐默认 30、晚餐备餐默认 45，步进 5 分钟，范围 0–180。
- 按钮：`生成就餐计划 ✔`

生成后：
- 写入演示 `D.plan`
- 早餐自动打卡状态取决于第 1 步开关
- 返回首页并 Toast：`就餐计划已生成，提醒已排程`

---

## 5. Record 记录页（统计概览 + 日历合并）

### 统计概览

- 连续打卡大数字：`12`
- 本月完成率：83%（16 格进度条，13 格点亮）
- 本周完成率：100%（12 格全亮）
- 近 7 天柱状图：7 根绿色像素柱，高度演示数据

### 月历

- 星期表头：一 二 三 四 五 六 日
- 每月单元格：日期数字 + 三个小点（早/午/晚）
  - 绿点 = 已打卡
  - 灰点 = 未打卡
- 今天有黄色 outline。
- 点击日期打开详情面板：
  - 显示日期 + 早餐/午餐/晚餐三行
  - 每行可「补打卡 / 已打卡」切换
  - 更新后同步重绘日历

### 切月

左右 `◀` / `▶` 按钮切换月份。

---

## 6. Settings 设置页

| 设置项 | 演示值 |
|---|---|
| 进食窗口 | 8 小时 |
| 餐间隔下限 | 3 小时 |
| 晚餐距窗口结束缓冲 | 30 分钟 |
| 默认备餐时长 | 早0 · 午30 · 晚45 |
| 提醒模式 | 通知 |
| 查看 8:16 说明 | 打开引导弹窗 |
| 关于 Fast16 | v0.1.0 |

---

## 7. Onboarding 引导弹窗

- 标题：`🍙 什么是 8:16 饮食法？`
- 内容：
  - 8h 进食 / 16h 断食分块条示意
  - 在 8 小时窗口里吃完三餐：记录早餐 → 自动排午/晚 → 到点提醒「备餐 / 开吃」→ 吃完打卡
  - 免责文案：「8:16 是一种进食时间安排，请结合自身情况」
- 按钮：`知道了，开始使用`
- 关闭后 Toast：`已记住，不再自动弹出`

---

## 8. Widget 桌面小组件预览

- 卡片布局：
  - 标题：`816 轻断食`
  - 右上：断食剩余 `断食 3:20`
  - 三餐行：早 / 午 / 晚 三个像素饭碗按钮
  - 下一餐：`下一餐 午餐 · 12:00 · 距备餐 02:13`
  - 操作按钮：`打卡`、`打开 App`
- 交互：
  - 点击三餐图标在原型中实际只切换「午餐演示状态」`wLunch`，不会分别切换早/晚；早餐/晚餐状态由 App 内打卡状态决定
  - `打卡` 按钮同样演示「午 已打卡」
- 视觉：图标未打卡时 `grayscale + brightness`，已打卡保持原色；文字/边框为静态像素风格。

---

## 9. 像素角色「小健」规格

- 尺寸：16×20 像素 Q 版人形。
- 配色：
  - 蓝运动服 `#41a6f6`
  - 黄纽扣 `#ffcd75`
  - 肤色 `#f2cfa0`
  - 发色 `#4a3628`
  - 黑描边/线条 `#0f0f1a`
- 状态帧：
  - 待机 `idle`：呼吸 + 眨眼（约 3.1s 闭 1 帧）
  - 断食 `fasting`：复用待机帧（原型阶段）
  - 备餐 `prep`：双手前伸 + 锅 + 蒸汽
  - 进食 `eat`：张嘴 + 碗 + 筷子
  - 休息 `rest`：星星/闪光 + 两侧闪烁（原型未做“比赞”手部细节）
- 渲染：Canvas 逐像素绘制，CSS `image-rendering:pixelated`，整数倍缩放。

---

## 10. 像素设计令牌（原型内）

| Token | 值 |
|---|---|
| 背景 `bg` | `#0f0f1a` |
| 面板 `panel` | `#1a1c2c` |
| 绿 `green` | `#38b764` |
| 黄 `yellow` | `#ffcd75` |
| 红 `red` | `#b13e53` |
| 蓝 `blue` | `#41a6f6` |
| 白 `white` | `#f4f4f4` |
| 灰 `gray` | `#7b7b8c` |
| 描边/墨色 | `#0f0f1a` |
| 像素阶梯圆角 | `cornerSteps≈3`，clip-path polygon |
| 间距 | 8px 网格体系 |
| 动效 | 步进帧、瞬移/闪烁为主；Toast 原型使用 0.25s CSS 过渡（演示简化） |

---

## 11. 布局与间距细节（从 HTML/CSS 提取）

### 页面框架

| 区域 | 尺寸/位置 |
|---|---|
| 页面舞台 `.stage` | `max-width:1080px`，flex `gap:56px`，`align-items:flex-start` |
| Widget 预览 `.widget-wrap` | `flex: 0 0 340px`，顶部留白 `8px` |
| Widget 卡片 `.widget` | `width:320px`，背景 `bg`，`border:2px solid #000`，`outline:2px solid #3a3a4a`，clip-path 像素圆角 |
| Widget 内层 `.widget-in` | `padding:12px`，`border:2px solid ink` |
| 手机框 `.phone` | `flex: 0 0 432px` |
| 手机屏 `.phone-screen` | `width:408px; height:884px; border-radius:36px; overflow:hidden` |
| 状态栏 `.statusbar` | `height:34px; padding:0 26px; z-index:40` |
| App 内容 `.app` | `position:absolute; inset:34px 0 0` |
| 页面滚动区 `.screen-body` | `flex:1; padding:14px 14px 76px; overflow-y:auto` |
| 底部导航 `.app-tabs` | `height:58px; border-top:2px solid ink` |
| 手势条 `.gestbar` | `bottom:7px; width:104px; height:4px` |

### 通用间距与组件

- 基础间距类：`mt8=8px`、`mt12=12px`、`mt16=16px`、`gap8=8px`。
- `.row` 默认 `gap:10px`，`align-items:center`。
- 面板 `.panel`：外层 `padding:3px` + 像素圆角；内层 `.panel-in`：`padding:12px`。
- 按钮 `.pbtn`：`padding:9px 14px`，字号 `12px`；小按钮 `.pbtn.sm`：`padding:6px 10px`，字号 `11px`。
- Chip `.chip`：`padding:5px 8px`，`border:2px solid ink`，选中态 `bg yellow + color ink`。
- 分隔线 `.hr`：`border-top:2px solid #23263a; margin:12px 0`。

### Home 具体位置

- 顶部标题区：`TODAY` + 日期摘要 + 右侧 streak chip。
- 角色舞台 `.char-stage`：`width:130px; height:152px; margin:0 auto`。
- 角色 Canvas：绝对定位 `left:50%; bottom:0`，`transform:translateX(-50%)`；示例缩放为 `64×80px`（16×20 的 4 倍）。
- 气泡 `.bubble`：`margin:10px auto 0; max-width:250px; padding:8px 12px; text-align:center`。
- 断食计时面板：`margin-top:12px`，内部居中。
- 三餐列表 `.meal-row`：`display:flex; align-items:center; gap:10px; padding:10px; border:2px solid ink; background:panel`，行间距 `8px`。
- 餐行图标 `img`：`width:34px; height:34px`。
- 餐行状态点 `.meal-dot`：`width:16px; height:16px; border:2px solid ink`。
- 角色状态演示 chips：`margin-top:16px`，chips 间距 `8px`。

### Record 具体位置

- 统计概览面板：连续打卡数字 `font-size:40px`；完成率进度条为 16/12 格。
- 近 7 天柱状图 `.stat-bars`：`height:52px; gap:8px`，每列 `.bar` 绿色 + 2px 黑边。
- 日历表头 `.cal-head`：左右 `◀/▶` + 月份。
- 日历网格 `.cal-grid`：`display:grid; grid-template-columns:repeat(7,1fr); gap:3px; margin-top:10px`。
- 日历格子 `.cal-cell`：`border:2px solid ink; background:panel; padding:4px 0 5px; text-align:center`。
- 日历点 `.cal-dots`：`display:flex; justify-content:center; gap:2px; margin-top:4px`，点尺寸 `4×4px`。
- 日期详情面板 `.panel`：`margin-top:16px`，行间距 `8px`。

### Settings 具体位置

- 设置行 `.set-row`：`display:flex; justify-content:space-between; padding:12px; border:2px solid ink; background:panel`，行间距 `8px`。

### Create 具体位置

- 第 1 步早餐时间面板：内部居中，步进器 `justify-content:center`。
- 步进器 `.stepper button`：`width:30px; height:30px`；数值 `.val`：`min-width:44px; text-align:center`。
- 候选卡 `.cand`：`padding:10px; border:2px solid ink; background:panel`，卡片间距 `8px`；选中 `border-color:yellow`。
- 备餐微调面板：`margin-top:16px`，行距 `12px`。
- 底部主按钮：`width:100%; margin-top:16px`。

---

## 12. 关键交互效果与弹窗设计

### 按钮与选择

- `.pbtn:active > span`：按下时 `transform:translateY(2px)`（原型是下沉 2px，不是 1px）。
- `.chip.on`：黄色底 + 深色字。
- `.cand.on`：黄色描边。
- 步进器点击即时更新数值。

### 打卡交互

- 首页三餐「打卡 / 已打卡」按钮：点击切换 `D.checkins`，并 Toast 提示「已打卡 / 已撤销打卡」。
- 打卡后首页角色状态自动派生：0 完成 → `fasting`，部分完成 → `eat`，全部完成 → `rest`。
- 记录页日历补打卡：点击日期打开详情，点击「补打卡 / 已打卡」切换该餐状态，并同步重绘日历。
- Widget：点击任意三餐图标只切换午餐演示状态；「打卡」按钮 Toast「小组件：午 已打卡（演示）」。

### Toast

- 位置：`position:absolute; left:50%; bottom:76px; transform:translateX(-50%)`。
- 样式：`background:ink; border:2px solid yellow; color:yellow; padding:8px 14px; font-size:11px`。
- 行为：显示后约 `1.6s` 自动消失；原型使用 `transition:all .25s`。
- 移动端演示会触发 `navigator.vibrate(80)`（约 80ms 震动）。

### Onboarding 弹窗

- 遮罩 `.overlay`：`position:absolute; inset:0; z-index:50; background:rgba(5,5,10,.86); display:none`，显示时 `.show` 改为 `flex` 居中。
- 弹窗 `.dialog`：`width:88%`，使用 `.panel` 像素圆角容器。
- 内容：标题 + 8h/16h 分块条 + 说明 + 免责文案。
- 按钮：`知道了，开始使用`，全宽，`margin-top:16px`。
- 关闭：点击按钮移除 `.show`，Toast「已记住，不再自动弹出」。

### 日历详情弹层

- 点击日期后在页面内展开 `.panel#cal-detail`（不是独立弹窗）。
- 头部：日期 + 「关闭」按钮。
- 内容：早餐/午餐/晚餐三行，每行右侧「补打卡 / 已打卡」按钮。


## 13. Agent 落地提示

- 将 Home / Record / Settings / Create 对应到 `feature/home`、`feature/record`、`feature/settings`、`feature/create`。
- Onboarding 不作为路由，由根层 overlay 控制。
- Record 是统计概览 + 日历合并页，不是两个 Tab。
- Widget 使用 Glance，原型中的动画能力需降级为静态位图 + 纯色块。
- 原型中的 `D.*` 内存状态只是演示；正式实现必须走 Room/DataStore + MVI/SSOT。
