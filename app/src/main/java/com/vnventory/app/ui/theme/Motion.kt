package com.vnventory.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.BackEventCompat
import kotlin.math.roundToInt

/** 只动画视觉状态，不插值真实金额。Compose 内置动画会遵循系统动画时长比例。 */
object ShelfMotion {
    const val Quick = 160
    const val Standard = 240
    const val Navigation = 300
    const val Back = 180
    const val BackRecovery = 140
    const val Chrome = 120
    val Easing = FastOutSlowInEasing
    val BackEasing = LinearOutSlowInEasing

    /** 返回比进入更短、起步更快；轻微位移，不叠加缩放或延迟。 */
    fun backEnter(direction: Int): EnterTransition =
        fadeIn(tween(Back, easing = BackEasing)) +
            slideInHorizontally(tween(Back, easing = BackEasing)) { -it / 24 * direction }

    fun backExit(direction: Int): ExitTransition =
        fadeOut(tween(Back, easing = BackEasing)) +
            slideOutHorizontally(tween(Back, easing = BackEasing)) { it / 16 * direction }

    /** 手势已走过的部分不再重播；接近完成时不会额外等一个完整返回动画。 */
    fun backFinishDuration(progress: Float): Int = (Back * (1f - progress.coerceIn(0f, 1f))).roundToInt()

    // 所有通道同长且线性，手势半程不会因短淡出而提前消失；方向取真实手势边缘。
    fun predictiveBackEnter(swipeEdge: Int): EnterTransition {
        val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1
        return fadeIn(tween(Back, easing = LinearEasing)) +
            slideInHorizontally(tween(Back, easing = LinearEasing)) { -it / 24 * direction }
    }

    fun predictiveBackExit(swipeEdge: Int): ExitTransition {
        val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1
        return fadeOut(tween(Back, easing = LinearEasing)) +
            slideOutHorizontally(tween(Back, easing = LinearEasing)) { it / 8 * direction }
    }
}
