package com.ly.fast16.core.designsystem.token

/**
 * 像素动效令牌（基建文档 §2.1 定值 + pixel-motion-guidelines）：
 * 全部为「帧 × 每帧毫秒」的阶跃时序，业务组件不得裸写毫秒（AGENTS.md §6）。
 * 禁平滑插值 / squash / 渐隐；帧时序统一收口在本令牌。
 */
object PixelMotion {

    /** 精灵/步进帧：120ms/帧（≈8fps 阶跃观感） */
    const val FRAME_MS: Long = 120

    /** 交互反馈整体落在 60–200ms 短促区间 */
    object Press {
        /** 按下反馈帧时长（按压反馈 ~80ms） */
        const val HOLD_MS: Long = 80
    }

    /** 闪烁提示：200ms × 3 次 */
    object Blink {
        const val HALF_PERIOD_MS: Long = 200
        const val TIMES: Int = 3
    }

    /** 弹窗弹出：60ms × N 步阶跃（无插值） */
    object Pop {
        const val STEP_MS: Long = 60
        const val STEPS: Int = 3
        /** 打字机帧速：60ms/字（Undertale / Stardew Valley 对话框 typewriter） */
        const val TYPE_MS: Long = 60
        /** 窗口收起阶跃步数（退场 scale 收起，60ms × 4，关闭快于展开） */
        const val EXIT_STEPS: Int = 4
    }

    /** 页面切换 wipe：200ms */
    const val WIPE_MS: Long = 200

    /** 打卡成功：图标「跳一格」+ 高亮闪帧（120–200ms 拖长） */
    object Success {
        const val FRAME_MS: Long = 120
    }
}
