package com.vnventory.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import com.vnventory.app.domain.model.Money
import com.vnventory.app.ui.collection.CollectionContent
import com.vnventory.app.ui.collection.CollectionUiState
import com.vnventory.app.ui.detail.CopyDetailContent
import com.vnventory.app.ui.detail.CopyDetailUiState
import com.vnventory.app.ui.home.HomeContent
import com.vnventory.app.ui.preview.ShelfPreviewData
import com.vnventory.app.ui.theme.VNventoryTheme
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
class UiPolishUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VNventoryTheme(darkTheme = dark) { Surface { Box(Modifier.fillMaxSize()) { content() } } }
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

    @Test fun `首页封面不显示副标题但两盒仍可分别点击`() {
        var selected = 0L
        show {
            Scaffold(bottomBar = { ShelfNavigationBar(0, {}) }) { padding ->
                HomeContent(ShelfPreviewData.stats, ShelfPreviewData.copies, {}, { selected = it }, {}, Modifier.padding(padding))
            }
        }
        compose.onNodeWithText("故事，收进书架").assertIsDisplayed()
        compose.onNodeWithText("每一个版本，每一盒", substring = true).assertDoesNotExist()
        compose.onNodeWithText("让刚到手的版本先登上书架").assertDoesNotExist()
        compose.onAllNodesWithText("初回限定版 · PC").assertCountEquals(0)
        compose.onNodeWithText("2026-09-01").assertDoesNotExist()
        compose.onNodeWithText("第 1 盒").performClick()
        assertEquals(101L, selected)
        compose.onNodeWithText("第 2 盒").performClick()
        assertEquals(102L, selected)
        capture("home-polished-light")
    }

    @Test fun `收藏详情保留封面下面的版本副标题`() {
        val copy = ShelfPreviewData.copies.first()
        show { CopyDetailContent(CopyDetailUiState(loading = false, copy = copy), {}) }
        compose.onNodeWithText(copy.releaseTitle!!).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(copy.vnTitle).assertIsDisplayed()
    }

    @Test fun `完整书架仍显示版本信息且视图图标可切换`() {
        show { CollectionContent(CollectionUiState(copies = ShelfPreviewData.copies, loading = false), {}, {}, {}, {}) }
        compose.onAllNodesWithText("初回限定版 · PC").assertCountEquals(2)
        compose.onNodeWithContentDescription("封面书架").assertIsSelected()
        compose.onNodeWithContentDescription("详细列表").performClick().assertIsSelected()
        compose.mainClock.advanceTimeBy(400)
        compose.onAllNodesWithText("初回限定版 · PC").assertCountEquals(2)
        compose.onNodeWithContentDescription("封面书架").performClick().assertIsSelected()
        capture("shelf-polished-light")
    }

    @Test fun `深色大字体首页操作和整套导航图标仍可达`() {
        var added = false
        var all = false
        val selected = mutableIntStateOf(0)
        show(dark = true, fontScale = 1.5f) {
            Scaffold(bottomBar = { ShelfNavigationBar(selected.intValue, { selected.intValue = it }) }) { padding ->
                HomeContent(ShelfPreviewData.stats, ShelfPreviewData.copies, { added = true }, {}, { all = true }, Modifier.padding(padding))
            }
        }
        compose.onNodeWithContentDescription("添加收藏").assertIsDisplayed().performClick()
        compose.onNodeWithText("查看全部").assertIsDisplayed().performClick()
        assertTrue(added)
        assertTrue(all)
        compose.onNodeWithText("首页").assertIsSelected()
        compose.onNodeWithText("书架").performClick().assertIsSelected()
        assertEquals(1, selected.intValue)
        compose.onNodeWithText("批次").assertIsDisplayed()
        compose.onNodeWithText("设置").assertIsDisplayed()
        capture("home-polished-large-dark")
    }

    @Test fun `首页紧凑封面也遵守价格展示开关和未知价格语义`() {
        val copy = ShelfPreviewData.copies.first().copy(priceMinor = null)
        show { HomeContent(ShelfPreviewData.stats.copy(showShelfPrices = true), listOf(copy), {}, {}, {}) }
        compose.onNodeWithText("未记录").assertIsDisplayed()
        compose.onNodeWithText(Money.formatWithCode(0, copy.currency)).assertDoesNotExist()
        compose.onNodeWithText(copy.releaseTitle!!).assertDoesNotExist()
    }

    @Test fun `紧凑搜索框保留输入清空和排序操作`() {
        val state = androidx.compose.runtime.mutableStateOf(CollectionUiState(copies = ShelfPreviewData.copies, loading = false))
        var sortChanged = false
        show {
            CollectionContent(state.value,
                { state.value = state.value.copy(query = state.value.query.copy(search = it)) },
                { sortChanged = true }, {}, {})
        }
        compose.onNode(hasSetTextAction()).performTextInput("星降る")
        compose.runOnIdle { assertEquals("星降る", state.value.query.search) }
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithContentDescription("清除搜索").performClick()
        compose.runOnIdle { assertEquals("", state.value.query.search) }
        compose.onNodeWithContentDescription("排序 · 最近添加").performClick()
        compose.onNodeWithText("最早添加").performClick()
        assertTrue(sortChanged)
    }
}
