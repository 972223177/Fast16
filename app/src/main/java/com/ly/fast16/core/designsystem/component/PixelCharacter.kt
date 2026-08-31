package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.domain.model.CharacterState
import kotlinx.coroutines.delay

/**
 * 像素角色「小健」（设计方案 §8.6 / 原型 index.html 角色资产直接移植）：
 * Q 版人形（16×20），蓝色运动服；待机呼吸起伏 + 自然眨眼，另有 备餐/进食/休息 三态道具。
 *
 * 帧数据与 UI 原型 Canvas 逐像素绘制同构（CH_IDLE1 等），仅需保持字符网格一致。
 *
 * @param state 角色状态（IDLE/FASTING 共用待机帧；PREP/EAT/REST 带道具）
 * @param scale 像素放大倍数（默认 4 → 64×80dp，与原型 64×80 显示一致）
 */
@Composable
fun PixelCharacter(
    state: CharacterState,
    modifier: Modifier = Modifier,
    scale: Float = 4f,
) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(state) {
        while (true) {
            delay(260)
            frame++
        }
    }

    val rows = when (state) {
        CharacterState.IDLE, CharacterState.FASTING ->
            if (frame % 12 == 10) CH_IDLE2 else CH_IDLE1 // 每 ~3.1s 眨眼 1 帧
        CharacterState.PREP -> CH_PREP
        CharacterState.EATING -> if (frame % 2 == 0) CH_EAT1 else CH_EAT2
        CharacterState.REST -> if (frame % 2 == 0) CH_REST1 else CH_REST2
    }

    val width = (16 * scale).dp
    val height = (20 * scale).dp

    Box(modifier = modifier.size(width, height)) {
        PixelSprite(rows = rows, modifier = Modifier.matchParentSize())
        // 道具：align 相对定位（相对角色四边，随 Box 缩放保持比例）+ 尺寸按 scale 等比例，
        // 不依赖绝对屏幕坐标 → 任意 density/屏幕尺寸不错位、不变形（compat §5 大屏自适应）
        when (state) {
            CharacterState.PREP -> {
                // 蒸汽（右上）+ 平底锅（左下）
                PixelSprite(
                    rows = if (frame % 2 == 0) PROP_STEAM1 else PROP_STEAM2,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = (1 * scale).dp)
                        .size((4 * scale).dp, (3 * scale).dp),
                )
                PixelSprite(
                    rows = PROP_PAN,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = (0.5 * scale).dp, bottom = (0.5 * scale).dp)
                        .size((11 * scale).dp, (4 * scale).dp),
                )
            }

            CharacterState.EATING -> {
                // 饭碗（左下）+ 筷子（右下偏上）
                PixelSprite(
                    rows = PROP_BOWL,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = (1 * scale).dp)
                        .size((10 * scale).dp, (6 * scale).dp),
                )
                PixelSprite(
                    rows = PROP_CHOP,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = (1 * scale).dp, bottom = (4 * scale).dp)
                        .size((1 * scale).dp, (6 * scale).dp),
                )
            }

            CharacterState.REST -> {
                // 星星 ×2（右上 / 左下）
                val spark = if (frame % 2 == 0) PROP_SPARK1 else PROP_SPARK2
                PixelSprite(
                    rows = spark,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = (0.5 * scale).dp, end = (0.5 * scale).dp)
                        .size((5 * scale).dp, (5 * scale).dp),
                )
                PixelSprite(
                    rows = spark,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = (0.5 * scale).dp, bottom = (1 * scale).dp)
                        .size((5 * scale).dp, (5 * scale).dp),
                )
            }

            else -> Unit
        }
    }
}

// ---------- 角色帧（16×20，原型 CH_IDLE1 等直接移植） ----------

/** 待机帧 1（呼吸上浮） */
private val CH_IDLE1 = listOf(
    "......HHHH......",
    "....HHHHHHHH....",
    "...HHHHHHHHHH...",
    "..HHSSSSSSSSHH..",
    "..HSSSSSSSSSSH..",
    "..HSKSSSSSSKSH..",
    "..HSSSSSSSSSSH..",
    "..HSSSSKKSSSSH..",
    "...HSSSSSSSSH...",
    "....HHHHHHHH....",
    "..KKBBBBBBBBKK..",
    ".KBBBBBBBBBBBBK.",
    ".KBBBBBBBBBBBBK.",
    ".KBYBBBBBBBBYBK.",
    ".KBBBBBBBBBBBBK.",
    "..KKBBBBBBBBKK..",
    "....KK....KK....",
    "....KK....KK....",
    "....KK....KK....",
    "...KKK....KKK...",
)

/** 待机帧 2（眨眼） */
private val CH_IDLE2 = CH_IDLE1.toMutableList().apply {
    this[5] = "..HSSSSSSSSSSH.."
}

/** 备餐：双手前伸（K=手） */
private val CH_PREP = CH_IDLE1.toMutableList().apply {
    this[11] = ".KBBKBBBBBBKBBK."
    this[12] = ".KBBBBBBBBBBBBK."
}

/** 进食帧 1：张嘴 + 举碗 */
private val CH_EAT1 = CH_IDLE1.toMutableList().apply {
    this[7] = "..HSSSSKKKSSSH.."
    this[12] = ".KBKBBBBBBBBKBK."
}

/** 进食帧 2：举碗 */
private val CH_EAT2 = CH_IDLE1.toMutableList().apply {
    this[12] = ".KBKBBBBBBBBKBK."
}

/** 休息帧 1 */
private val CH_REST1 = listOf(
    "K....HHHH....K",
    "K...HHHHHH...K",
    "K..HHHHHHHH..K",
    "..HHSSSSSSSSHH..",
    "..HSSSSSSSSSSH..",
    "..HSKSSSSSSKSH..",
    "..HSSSSSSSSSSH..",
    "..HSSSSKKSSSSH..",
    "...HSSSSSSSSH...",
    "....HHHHHHHH....",
    "..KKBBBBBBBBKK..",
    ".KBBBBBBBBBBBBK.",
    ".KBBBBBBBBBBBBK.",
    ".KBYBBBBBBBBYBK.",
    ".KBBBBBBBBBBBBK.",
    "..KKBBBBBBBBKK..",
    "....KK....KK....",
    "....KK....KK....",
    "....KK....KK....",
    "...KKK....KKK...",
)

/** 休息帧 2（与帧 1 同，供 2 帧循环） */
private val CH_REST2 = CH_REST1.toMutableList()

// ---------- 道具（原型 PROP_* 直接移植） ----------

/** 平底锅 */
private val PROP_PAN = listOf(
    "..KYYYYYYK..",
    ".KYYYYYYYYK.",
    ".KYYYYYYYYK.",
    "..KKKKKKKK..",
)

/** 蒸汽帧 1 */
private val PROP_STEAM1 = listOf(".W..", "WW..", "W...")

/** 蒸汽帧 2 */
private val PROP_STEAM2 = listOf("..W.", ".WW.", "WW..")

/** 饭碗 */
private val PROP_BOWL = listOf(
    ".WWWWWWWW.",
    "KWWWWWWWWK",
    "KWWWWWWWWK",
    "KWWWWWWWWK",
    ".KKKKKKKK.",
    "..K....K..",
)

/** 筷子 */
private val PROP_CHOP = listOf("Y", "Y", "Y", "Y", "Y", "Y")

/** 星星帧 1 */
private val PROP_SPARK1 = listOf("..Y..", ".Y.Y.", "..Y..", ".....")

/** 星星帧 2 */
private val PROP_SPARK2 = listOf(".....", "..W..", ".WYW.", ".W.W.", ".....")

/** 餐次碗图标（8×8，PixelMealRow 共用） */
internal val ICON_BOWL = listOf(
    ".GGGGGG..",
    "GWWWWWWG.",
    "WWWWWWWW.",
    "WWWWWWWW.",
    "KWWWWWWWK",
    "KWWWWWWWK",
    ".KKKKKKK.",
    "..K...K..",
)

@PreviewPixel
@Composable
private fun PixelCharacterPreview() {
    PixelTheme {
        Box(contentAlignment = Alignment.Center) {
            PixelCharacter(state = CharacterState.PREP)
        }
    }
}
