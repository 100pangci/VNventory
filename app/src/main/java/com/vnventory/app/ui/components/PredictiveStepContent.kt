package com.vnventory.app.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.vnventory.app.ui.theme.ShelfMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 步骤返回跟随系统手势；提交前不改业务状态，取消不会清空选择和表单。 */
@Composable
internal fun PredictiveStepContent(
    step: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable AnimatedContentScope.(Int) -> Unit,
) {
    val state = remember { SeekableTransitionState(step) }
    val transition = rememberTransition(state, label = "addStep")
    val scope = rememberCoroutineScope()
    val reducedMotion = scope.coroutineContext[MotionDurationScale]?.scaleFactor == 0f
    var predictingBack by remember { mutableStateOf(false) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var recoveryJob by remember { mutableStateOf<Job?>(null) }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1

    LaunchedEffect(step, reducedMotion) {
        recoveryJob?.cancelAndJoin()
        if (!predictingBack) {
            if (reducedMotion) {
                state.snapTo(step)
            } else {
                // SeekableTransitionState 默认可能沿用旧（进入）转场的 300ms 时钟。
                // 显式指定进度时长，线性推进时间轴，视觉缓动由子转场负责。
                val duration = if (step < state.currentState) ShelfMotion.Back else ShelfMotion.Navigation
                state.animateTo(step, animationSpec = tween(duration, easing = LinearEasing))
            }
        }
    }

    PredictiveBackHandler(enabled = step > 0 && enabled) { events ->
        val origin = step
        recoveryJob?.cancelAndJoin()
        if (state.currentState != origin) state.snapTo(origin)
        predictingBack = true
        try {
            events.collect { event ->
                swipeEdge = event.swipeEdge
                state.seekTo(event.progress.coerceIn(0f, 1f), origin - 1)
            }
            if (reducedMotion) state.snapTo(origin - 1) else {
                state.animateTo(
                    origin - 1,
                    animationSpec = tween(ShelfMotion.backFinishDuration(state.fraction), easing = ShelfMotion.BackEasing),
                )
            }
            onBack()
            predictingBack = false
        } catch (_: CancellationException) {
            // 手势任务已取消；回弹用组件自己的作用域，组件销毁时也会自动取消。
            recoveryJob = scope.launch {
                try {
                    if (reducedMotion) {
                        state.snapTo(origin)
                        return@launch
                    }
                    // 保持同一个 origin → previous 转场，倒放手势进度。
                    // 直接 animateTo(origin) 会切换转场方向，导致先向外跳一下再回弹。
                    val progress = Animatable(state.fraction)
                    coroutineScope {
                        val seeking = launch(start = CoroutineStart.UNDISPATCHED) {
                            snapshotFlow { progress.value }.collect { state.seekTo(it, origin - 1) }
                        }
                        progress.animateTo(0f, tween(ShelfMotion.BackRecovery, easing = ShelfMotion.BackEasing))
                        seeking.cancelAndJoin()
                    }
                    state.seekTo(0f, origin - 1)
                    state.snapTo(origin)
                } finally { predictingBack = false }
            }
        }
    }

    transition.AnimatedContent(
        modifier = modifier,
        transitionSpec = {
            if (predictingBack) {
                (ShelfMotion.predictiveBackEnter(swipeEdge) togetherWith
                    ShelfMotion.predictiveBackExit(swipeEdge)).using(null)
            } else if (targetState < initialState) {
                (ShelfMotion.backEnter(direction) togetherWith ShelfMotion.backExit(direction)).using(null)
            } else {
                val sign = direction
                (fadeIn(tween(ShelfMotion.Navigation, easing = ShelfMotion.Easing)) +
                    slideInHorizontally(tween(ShelfMotion.Navigation, easing = ShelfMotion.Easing)) { it / 12 * sign }) togetherWith
                    (fadeOut(tween(ShelfMotion.Navigation, easing = ShelfMotion.Easing)) +
                        slideOutHorizontally(tween(ShelfMotion.Navigation, easing = ShelfMotion.Easing)) { -it / 12 * sign })
            }
        },
        content = content,
    )
}
