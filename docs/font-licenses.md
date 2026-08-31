# 字体授权记录

项目内置两个像素字体，均为**开源可商用**（OFL 1.1），按设计方案 §8.3「落地前确认字体授权」要求在此登记。

## Press Start 2P（数字/英文/计时）

- 用途：`PixelType.digital` —— 计时数字、英文、强调数字
- 文件：`app/src/main/res/font/press_start_2p.ttf`
- 许可：SIL Open Font License 1.1（可商用、可修改、可再分发，需保留版权与许可声明）
- 版权：Copyright 2011 The Press Start 2P Project Authors
- 来源：Google Fonts（github.com/google/fonts，ofl/pressstart2p）
- 版本：Regular（单字重）

## 缝合像素字体 Fusion Pixel（中文标题/强调）

- 用途：`PixelType.cjk` —— 中文标题、强调文案、短正文（像素化）
- 文件：`app/src/main/res/font/fusion_pixel_12px.ttf`
- 许可：SIL Open Font License 1.1（可商用；子集化属 OFL 允许的修改）
- 版权：TakWolf（方舟像素/缝合像素项目，github.com/TakWolf/fusion-pixel-font）
- 来源：Release 2026.08.11，`fusion-pixel-font-12px-monospaced-ttf` 包内 `fusion-pixel-12px-monospaced-zh_hans.ttf`
- **子集化说明**：原文件 6.7MB（全量中日韩），本项目用 `pyftsubset`（fontTools 4.63.0）按「仓库内实际使用字符」（745 字：中文 + ASCII + 标点，见下文）裁剪至 120KB。若未来新增文案含未收录汉字，需重新执行子集化：
  ```bash
  python3 -m venv /tmp/fontenv && /tmp/fontenv/bin/pip install fonttools brotli
  # 收集项目字符 → chars.txt
  /tmp/fontenv/bin/pyftsubset <full-zh_hans.ttf> --text-file=chars.txt \
    --output-file=app/src/main/res/font/fusion_pixel_12px.ttf --no-hinting
  ```

## 说明

- 正文（`PixelType.bodyFamily`）使用系统黑体，不涉及第三方授权。
- 气泡文案中的 emoji（🍚🍳💪）两个字体均不含字形，由 Android 系统字体自动回退渲染。
