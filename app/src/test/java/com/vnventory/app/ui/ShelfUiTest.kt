package com.vnventory.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.ui.text.resolve

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.vnventory.app.domain.model.CollectionSort
import com.vnventory.app.ui.collection.CollectionContent
import com.vnventory.app.ui.collection.CollectionUiState
import com.vnventory.app.ui.components.AddStepIndicator
import com.vnventory.app.ui.components.SaveButton
import com.vnventory.app.ui.detail.CopyDetailContent
import com.vnventory.app.ui.detail.CopyDetailUiState
import com.vnventory.app.ui.home.HomeContent
import com.vnventory.app.ui.home.HomeStats
import com.vnventory.app.ui.preview.ShelfPreviewData
import com.vnventory.app.ui.theme.VNventoryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Native Graphics 实际渲染与交互验证，不把编译通过当作界面验证。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShelfUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VNventoryTheme(darkTheme = dark) {
                    Surface { Box(Modifier.fillMaxSize()) { content() } }
                }
            }
        }
    }

    private fun capture(name: String, covers: Int = 0) {
        if (covers > 0) compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "封面已加载"), useUnmergedTree = true)
                .fetchSemanticsNodes().size >= covers
        }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        // Coil 的 CrossfadePainter 使用墙上单调时钟，不跟随 Compose 虚拟动画时钟。
        // 等待图片淡入的真实时长，再绘制一帧；不能把“已解码”误当成“已显示”。
        if (covers > 0) {
            val started = TimeSource.Monotonic.markNow()
            compose.waitUntil { started.elapsedNow() >= 300.milliseconds }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        val directory = File(checkNotNull(System.getProperty("vnventory.screenshot.dir"))).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun `浅色首页封面陈列可点入独立盒并截图`() {
        var selected: Long? = null
        show { HomeContent(ShelfPreviewData.stats, ShelfPreviewData.copies, {}, { selected = it }, {}) }
        compose.onNodeWithText("故事，收进书架").assertIsDisplayed()
        capture("home-light", covers = 3)
        compose.onNodeWithText("未拆 · 第 1 盒").performClick()
        assertEquals(101L, selected)
    }

    @Test fun `深色首页与金额构成展开截图`() {
        show(dark = true) { HomeContent(ShelfPreviewData.stats.copy(showPriceStats = true), ShelfPreviewData.copies, {}, {}, {}) }
        capture("home-dark", covers = 3)
        compose.onNodeWithText("查看支出构成").performScrollTo().performClick()
        compose.onNodeWithText("运费").performScrollTo().assertIsDisplayed()
        capture("home-dark-costs")
    }

    @Test fun `空书架有明确添加入口`() {
        var added = false
        show { HomeContent(HomeStats(), emptyList(), { added = true }, {}, {}) }
        compose.onNodeWithText("添加第一盒").performScrollTo().performClick()
        assertTrue(added)
        capture("home-empty")
    }

    @Test fun `同版多盒分别存在列表切换排序交互可用`() {
        var sort: CollectionSort? = null
        var selected: Long? = null
        show { CollectionContent(CollectionUiState(copies = ShelfPreviewData.copies, loading = false), {}, { sort = it }, {}, { selected = it }) }
        capture("collection-grid", covers = 3)
        compose.onNodeWithContentDescription("详细列表").performClick().assertIsSelected()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("第 2 盒").performScrollTo().performClick()
        assertEquals(102L, selected)
        capture("collection-list")
        compose.onNodeWithContentDescription("排序 · 最近添加").performClick()
        compose.onNodeWithText(ApplicationProvider.getApplicationContext<Context>().resources.resolve(CollectionSort.PRICE_ASC.label)).performClick()
        assertEquals(CollectionSort.PRICE_ASC, sort)
    }

    @Test fun `大字体收藏页保持操作入口可见并截图`() {
        show(dark = true, fontScale = 1.5f) { CollectionContent(CollectionUiState(copies = ShelfPreviewData.copies, loading = false), {}, {}, {}, {}) }
        compose.onNodeWithContentDescription("封面书架").assertIsDisplayed()
        compose.onNodeWithContentDescription("详细列表").performClick()
        compose.onNodeWithText("第 1 盒").performScrollTo().assertIsDisplayed()
        capture("collection-large-type", covers = 2)
    }

    @Test fun `独立收藏详情也突出准确最终成本`() {
        show { CopyDetailContent(CopyDetailUiState(loading = false, copy = ShelfPreviewData.copies.first()), {}) }
        capture("detail-poster", covers = 1)
        compose.onNodeWithText("这盒的最终实际成本").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("当前为独立收藏，成本等于购入价格。").assertIsDisplayed()
        capture("detail-cost")
    }

    @Test fun `步骤进度动画有中间帧且最终到达正确进度`() {
        val step = mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        show { AddStepIndicator(step.intValue) }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { step.intValue = 1; Snapshot.sendApplyNotifications() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        val matcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        val progress = compose.onNode(matcher).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue("mid-animation progress=$progress", progress > 1f / 3f && progress < 2f / 3f)
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val final = compose.onNode(matcher).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertEquals(2f / 3f, final, .001f)
    }

    @Test fun `保存中禁用重复操作并显示反馈`() {
        show { SaveButton("放入我的书架", saving = true, enabled = true, onClick = {}) }
        compose.onNodeWithText("正在保存…").assertIsNotEnabled()
    }
}
