package com.vnventory.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.vnventory.app.ui.components.AddStepIndicator
import com.vnventory.app.ui.components.VnCover
import com.vnventory.app.ui.components.PredictiveStepContent
import androidx.compose.material3.Text
import com.vnventory.app.ui.preview.ShelfPreviewData
import com.vnventory.app.ui.theme.VNventoryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReducedMotionUiTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun `动画比例为零时进度立即到达目标`() {
        val step = mutableIntStateOf(0)
        compose.setContent { VNventoryTheme { Surface { AddStepIndicator(step.intValue) } } }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { step.intValue = 2; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        val info = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(1f, info.current, .001f)
    }

    @Test fun `减少动态时图片直接显示而不是淡入空白`() {
        compose.setContent {
            VNventoryTheme {
                Surface {
                    Column {
                        VnCover(ShelfPreviewData.copies.first().coverUrl, "原创预览海报", Modifier.width(200.dp).height(280.dp).testTag("cover"))
                    }
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "封面已加载"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("cover").captureToImage().asAndroidBitmap()
        // 原创海报左上区域为 #202D55；若仍是渐变占位则这个断言失败。
        assertEquals(0xFF202D55.toInt(), bitmap.getPixel(bitmap.width / 10, bitmap.height / 10))
    }

    @Test fun `减少动态时步骤返回不等待180ms转场`() {
        val step = mutableIntStateOf(2)
        compose.setContent {
            VNventoryTheme {
                Surface {
                    PredictiveStepContent(step.intValue, { step.intValue-- }) { page ->
                        Text("步骤 $page", Modifier.testTag("step-$page"))
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { step.intValue = 1; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.onNodeWithTag("step-2").assertDoesNotExist()
        compose.onNodeWithTag("step-1").assertIsDisplayed()
    }
}
