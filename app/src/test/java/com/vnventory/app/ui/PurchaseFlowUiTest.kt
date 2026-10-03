package com.vnventory.app.ui

import android.graphics.Bitmap
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.ReleaseInfo
import com.vnventory.app.domain.model.VnInfo
import com.vnventory.app.ui.add.AddFlowUiState
import com.vnventory.app.ui.add.PurchaseFormContent
import com.vnventory.app.ui.add.PurchaseFormState
import com.vnventory.app.ui.add.ReleasesUiState
import com.vnventory.app.ui.components.PredictiveStepContent
import com.vnventory.app.ui.preview.ShelfPreviewData
import com.vnventory.app.ui.theme.VNventoryTheme
import com.vnventory.app.ui.theme.ShelfMotion
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PurchaseFlowUiTest {
    @get:Rule val compose = createComposeRule()

    private val sample = AddFlowUiState(
        selectedVn = VnInfo("v1", "Hoshi Furu Yoru no Kioku", "星降る夜の記憶", null, ShelfPreviewData.copies.first().coverUrl, null),
        releases = ReleasesUiState(releases = listOf(
            ReleaseInfo("r1", "v1", "初回限定版", "2026-09-01", listOf("win"), listOf("ja"), emptyList(), null, null, true, null, null),
        )),
        form = PurchaseFormState(releaseId = "r1", releaseTitle = "初回限定版", priceText = "6800", currency = "JPY"),
    )

    private fun show(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VNventoryTheme { Surface { Box(Modifier.fillMaxSize()) { content() } } }
            }
        }
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val directory = File(checkNotNull(System.getProperty("vnventory.screenshot.dir"))).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun `购入分区清楚日语为主标题且数量只影响商品小计`() {
        val state = mutableStateOf(sample)
        show { PurchaseFormContent(state.value, { state.value = state.value.copy(form = it) }) }
        compose.onNodeWithText("所选版本").assertIsDisplayed()
        compose.onNodeWithText("星降る夜の記憶").assertIsDisplayed()
        compose.onNodeWithText("单盒价格").assertIsDisplayed()
        capture("purchase-form-top")
        compose.onNodeWithContentDescription("增加数量").performScrollTo().performClick()
        compose.onNodeWithText("¥13,600 JPY").performScrollTo().assertIsDisplayed()
        assertEquals("6800", state.value.form.priceText)
        assertEquals(2, state.value.form.quantity)
        compose.onNodeWithText("店铺 / 渠道").performScrollTo().performTextReplacement("駿河屋")
        compose.onNodeWithText("所属购买批次").performScrollTo().assertIsDisplayed()
        assertEquals("駿河屋", state.value.form.shop)
        capture("purchase-form-records")
        compose.onNodeWithText("备注", substring = false).performScrollTo().assertIsDisplayed()
    }

    @Test fun `大字体下品相换行自定义说明和购买记录可达`() {
        val state = mutableStateOf(sample)
        show(fontScale = 1.5f) { PurchaseFormContent(state.value, { state.value = state.value.copy(form = it) }) }
        compose.onNodeWithText(CopyCondition.CUSTOM.label).performScrollTo().performClick()
        compose.onNodeWithText("自定义品相说明").performScrollTo().performTextReplacement("缺少说明书")
        assertEquals("缺少说明书", state.value.form.conditionNote)
        capture("purchase-form-large-type")
        compose.onNodeWithText("所属购买批次").performScrollTo().assertIsDisplayed()
    }

    @Test fun `预测返回半程不提交取消保持当前步骤并能再次完成返回`() {
        val step = mutableIntStateOf(2)
        var committed = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        show {
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            PredictiveStepContent(step.intValue, { committed++; step.intValue-- }, Modifier.fillMaxSize()) { page ->
                Surface(Modifier.fillMaxSize().testTag("page-$page")) { Text("步骤 $page") }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(event(0f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(event(.5f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(2, step.intValue)
        assertEquals(0, committed)
        compose.onNodeWithTag("page-2").assertIsDisplayed()
        val halfway = compose.onNodeWithTag("page-2").fetchSemanticsNode().boundsInRoot.left
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.mainClock.advanceTimeBy(48)
        val returning = compose.onNodeWithTag("page-2").fetchSemanticsNode().boundsInRoot.left
        assertTrue("halfway=$halfway returning=$returning", returning >= 0f && returning <= halfway)
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals(2, step.intValue)
        assertEquals(0, committed)
        compose.onNodeWithText("步骤 2").assertIsDisplayed()

        compose.runOnIdle { dispatcher.dispatchOnBackStarted(event(0f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(event(.7f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals(1, step.intValue)
        assertEquals(1, committed)
        compose.onNodeWithText("步骤 1").assertIsDisplayed()
    }

    @Test fun `右侧返回手势连续跟手向左移动不是松手后才启动`() {
        val step = mutableIntStateOf(2)
        lateinit var dispatcher: OnBackPressedDispatcher
        show {
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            PredictiveStepContent(step.intValue, { step.intValue-- }, Modifier.fillMaxSize()) { page ->
                Surface(Modifier.fillMaxSize().testTag("page-$page")) { Text("步骤 $page") }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(event(0f, BackEventCompat.EDGE_RIGHT)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(event(.2f, BackEventCompat.EDGE_RIGHT)) }
        compose.mainClock.advanceTimeByFrame()
        val early = compose.onNodeWithTag("page-2").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(event(.8f, BackEventCompat.EDGE_RIGHT)) }
        compose.mainClock.advanceTimeByFrame()
        val late = compose.onNodeWithTag("page-2").fetchSemanticsNode().boundsInRoot
        assertTrue("early=$early late=$late", late.right < early.right)
        assertEquals(2, step.intValue)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithText("步骤 2").assertIsDisplayed()
    }

    @Test fun `普通步骤返回在180ms内完成不会等待进入动画时长`() {
        val step = mutableIntStateOf(2)
        show {
            PredictiveStepContent(step.intValue, { step.intValue-- }, Modifier.fillMaxSize()) { page ->
                Surface(Modifier.fillMaxSize().testTag("step-$page")) { Text("步骤 $page") }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { step.intValue = 1; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("step-2").assertExists()
        compose.mainClock.advanceTimeBy(ShelfMotion.Back - 64 + 32L)
        compose.waitForIdle()
        compose.onNodeWithTag("step-2").assertDoesNotExist()
        compose.onNodeWithText("步骤 1").assertIsDisplayed()
    }

    @Test fun `高进度手势松手只完成剩余进度且只提交一次`() {
        val step = mutableIntStateOf(2)
        var committed = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        show {
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            PredictiveStepContent(step.intValue, { committed++; step.intValue-- }, Modifier.fillMaxSize()) { page ->
                Surface(Modifier.fillMaxSize().testTag("step-$page")) { Text("步骤 $page") }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(event(0f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(event(.8f)) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(2, step.intValue)
        assertEquals(0, committed)
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(ShelfMotion.backFinishDuration(.8f).toLong() + 48)
        compose.waitForIdle()
        assertEquals(1, step.intValue)
        assertEquals(1, committed)
        compose.onNodeWithText("步骤 1").assertIsDisplayed()
        compose.onNodeWithTag("step-2").assertDoesNotExist()
    }

    @Test fun `页面普通返回在短转场结束后移除退出页`() {
        lateinit var nav: NavHostController
        show {
            nav = rememberNavController()
            NavHost(
                nav, startDestination = "shelf", modifier = Modifier.fillMaxSize(),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { ShelfMotion.backEnter(1) },
                popExitTransition = { ShelfMotion.backExit(1) },
            ) {
                composable("shelf") { Surface(Modifier.fillMaxSize()) { Text("书架") } }
                composable("detail") { Surface(Modifier.fillMaxSize().testTag("detail")) { Text("收藏档案") } }
            }
        }
        compose.runOnIdle { nav.navigate("detail") }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { nav.popBackStack() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("detail").assertExists()
        compose.mainClock.advanceTimeBy(ShelfMotion.Back - 64 + 32L)
        compose.waitForIdle()
        assertEquals("shelf", nav.currentDestination?.route)
        compose.onNodeWithTag("detail").assertDoesNotExist()
        compose.onNodeWithText("书架").assertIsDisplayed()
    }

    @Test fun `页面导航预测返回取消不出栈完成后只出栈一次`() {
        lateinit var dispatcher: OnBackPressedDispatcher
        lateinit var nav: NavHostController
        show {
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            nav = rememberNavController()
            NavHost(
                nav, startDestination = "shelf", modifier = Modifier.fillMaxSize(),
                predictivePopEnterTransition = { edge -> ShelfMotion.predictiveBackEnter(edge) },
                predictivePopExitTransition = { edge -> ShelfMotion.predictiveBackExit(edge) },
            ) {
                composable("shelf") { Surface(Modifier.fillMaxSize()) { Text("书架") } }
                composable("detail") { Surface(Modifier.fillMaxSize().testTag("detail")) { Text("收藏档案") } }
            }
        }
        compose.runOnIdle { nav.navigate("detail") }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(event(0f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(event(.5f)) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("detail", nav.currentDestination?.route)
        compose.onNodeWithTag("detail").assertIsDisplayed()
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals("detail", nav.currentDestination?.route)
        compose.onNodeWithText("收藏档案").assertIsDisplayed()

        compose.runOnIdle { dispatcher.dispatchOnBackStarted(event(0f, BackEventCompat.EDGE_RIGHT)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(event(.7f, BackEventCompat.EDGE_RIGHT)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        assertEquals("shelf", nav.currentDestination?.route)
        compose.onNodeWithText("书架").assertIsDisplayed()
    }

    private fun event(progress: Float, edge: Int = BackEventCompat.EDGE_LEFT) = BackEventCompat(0f, 100f, progress, edge)
}
