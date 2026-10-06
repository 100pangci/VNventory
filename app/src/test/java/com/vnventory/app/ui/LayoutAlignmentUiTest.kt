package com.vnventory.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vnventory.app.domain.cost.CostEngine
import com.vnventory.app.domain.model.*
import com.vnventory.app.ui.components.*
import com.vnventory.app.ui.orders.OrderDetailContent
import com.vnventory.app.ui.preview.ShelfPreviewData
import com.vnventory.app.ui.settings.AppearancePreferencesContent
import com.vnventory.app.ui.settings.ShopEditorDialog
import com.vnventory.app.ui.settings.ShopEditorState
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
@Config(sdk = [34], qualifiers = "w360dp-h800dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LayoutAlignmentUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(scale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                VNventoryTheme(darkTheme = true) { Surface { Box(Modifier.fillMaxSize()) { content() } } }
            }
        }
    }

    private fun capture(name: String, node: SemanticsNodeInteraction = compose.onRoot()) {
        compose.waitForIdle()
        val folder = File(checkNotNull(System.getProperty("vnventory.screenshot.dir"))).apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            assertTrue(node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun `横排金额与币种输入框顶部及边框高度一致`() {
        show {
            Column(Modifier.padding(24.dp)) {
                MoneyInputField("", "CNY", "金额", {}, {}, supportingText = "留空表示未记录")
            }
        }
        val amount = compose.onNodeWithTag("money-input").fetchSemanticsNode().boundsInRoot
        val currency = compose.onNodeWithTag("money-currency").fetchSemanticsNode().boundsInRoot
        assertEquals(amount.top, currency.top, 1f)
        assertTrue(currency.left > amount.right)
        compose.onNodeWithText("CNY").performClick()
        compose.onNodeWithText("JPY（¥）").performClick()
        capture("layout-money-aligned")
    }

    @Test fun `货币单选等宽等高且末行不拉伸`() {
        val selected = mutableStateOf("CNY")
        show {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                OptionGrid(Money.commonCurrencies, { "$it（${Money.symbol(it)}）" }, selected.value, { selected.value = it }, maxColumns = 2)
            }
        }
        val cny = compose.onNodeWithText("CNY（¥）").fetchSemanticsNode().boundsInRoot
        val jpy = compose.onNodeWithText("JPY（¥）").fetchSemanticsNode().boundsInRoot
        val usd = compose.onNodeWithText("USD（$）").fetchSemanticsNode().boundsInRoot
        val cad = compose.onNodeWithText("CAD（C$）").fetchSemanticsNode().boundsInRoot
        assertEquals(cny.width, jpy.width, 1f)
        assertEquals(cny.height, jpy.height, 1f)
        assertEquals(cny.top, jpy.top, 1f)
        assertEquals(cny.left, usd.left, 1f)
        assertEquals(cny.width, cad.width, 1f)
        compose.onNodeWithText("HKD（HK$）").performClick().assertIsSelected()
        assertEquals("HKD", selected.value)
        capture("layout-currency-grid")
    }

    @Test fun `大字体长选项完整显示且禁用项不能选择`() {
        val labels = listOf("平均分摊", "按价格比例", "手动指定")
        var selected = "平均分摊"
        show(1.8f) {
            Column(Modifier.padding(40.dp)) {
                OptionGrid(labels, { it }, selected, { selected = it }, enabled = { it != "按价格比例" })
            }
        }
        compose.onNodeWithText("按价格比例").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("手动指定").performClick()
        assertEquals("手动指定", selected)
        capture("layout-options-large")
    }

    @Test fun `开关持续按压反馈裁切在圆角内且松手只切换一次`() {
        val appearance = mutableStateOf(AppearancePreferences())
        var changes = 0
        show {
            Column(Modifier.padding(24.dp)) {
                AppearancePreferencesContent(appearance.value, {}, {
                    changes++
                    appearance.value = appearance.value.copy(dynamicColor = it)
                })
            }
        }
        val row = compose.onNodeWithTag("dynamic-color-row")
        val before = row.captureToImage().asAndroidBitmap()
        row.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        val pressed = row.captureToImage().asAndroidBitmap()
        assertEquals(before.getPixel(0, 0), pressed.getPixel(0, 0))
        assertEquals(before.getPixel(before.width - 1, 0), pressed.getPixel(pressed.width - 1, 0))
        assertFalse("Press feedback must be visible", before.sameAs(pressed))
        assertEquals(0, changes)
        capture("layout-switch-pressed")
        row.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, changes)
        assertTrue(appearance.value.dynamicColor)
    }

    @Test fun `大字体详情标签和值上下排列避免固定列挤压`() {
        show(1.5f) {
            Column(Modifier.padding(24.dp)) {
                LabeledRow("默认币种") { Text("CNY") }
            }
        }
        val label = compose.onNodeWithText("默认币种").fetchSemanticsNode().boundsInRoot
        val value = compose.onNodeWithText("CNY").fetchSemanticsNode().boundsInRoot
        assertTrue(value.top >= label.bottom)
        assertEquals(label.left, value.left, 1f)
    }

    @Test fun `不同字号标签和值首行基线对齐且长标签独占整行`() {
        show {
            Column(Modifier.padding(24.dp)) {
                LabeledRow("默认币种") { Text("CNY", style = MaterialTheme.typography.titleLarge) }
                LabeledRow("已记录商品与费用") { Text("¥6,900 JPY") }
            }
        }
        fun baseline(text: String): Float {
            val node = compose.onNodeWithText(text)
            val results = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
            return node.fetchSemanticsNode().boundsInRoot.top + results.single().firstBaseline
        }
        assertEquals(baseline("默认币种"), baseline("CNY"), 1f)
        val label = compose.onNodeWithText("已记录商品与费用").fetchSemanticsNode().boundsInRoot
        val amount = compose.onNodeWithText("¥6,900 JPY").fetchSemanticsNode().boundsInRoot
        assertTrue(amount.top >= label.bottom)
        capture("layout-label-baselines")
    }

    @Test fun `长金额多币种订单不挤压收藏标题且删除操作可达`() {
        val copy = ShelfPreviewData.copies.first().copy(priceMinor = 123456789012345L)
        val expenses = listOf(Expense(1, 1, "INTERNATIONAL_SHIPPING", ExpenseCategory.INTERNATIONAL_SHIPPING, 1234567890123L, "CNY", AllocationMode.EQUAL, null, 0))
        val detail = OrderDetail(PurchaseOrder(1, "长金额批次", null, null, "JPY", null, 0, 0), listOf(copy), expenses,
            CostEngine.computeOrderCosts(listOf(copy.costInput()), expenses.map { it.costInput() }))
        var removed = false
        var deleted = false
        show(1.5f) { OrderDetailContent(detail, {}, {}, {}, {}, { deleted = true }, { removed = true }) }
        compose.onNodeWithText(copy.vnTitle).performScrollTo().assertIsDisplayed()
        val title = compose.onNodeWithText(copy.vnTitle).fetchSemanticsNode().boundsInRoot
        assertTrue(title.width > compose.density.density * 150f)
        compose.onNodeWithText("移出").performScrollTo().performClick()
        assertTrue(removed)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("删除费用"))
        compose.onNodeWithContentDescription("删除费用").performClick()
        assertTrue(deleted)
        capture("layout-order-long-money")
    }

    @Test fun `店铺编辑长提示滚动时保存和取消固定可见`() {
        show(1.8f) {
            ShopEditorDialog(ShopEditorState(open = true, name = "测试店铺"), false, {}, {}, {},
                error = { Text("长错误提示".repeat(80)) })
        }
        compose.onNodeWithText("保存").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
        capture("layout-shop-dialog-large", compose.onNode(isDialog()))
    }

    @Test fun `同页批次和渠道菜单都跟随输入框宽度且选中可清空`() {
        val selected = mutableStateOf<Long?>(null)
        val shop = mutableStateOf("")
        val orders = listOf(OrderSummary(PurchaseOrder(1, "123", null, null, "CNY", null, 0, 0), 0, emptyMap(), emptyMap()))
        show {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ShopChannelField(shop.value, { shop.value = it }, listOf("参"), Modifier.testTag("shop-anchor"))
                OrderSelector(orders, selected.value, { selected.value = it }, Modifier.testTag("order-anchor"))
            }
        }
        val orderWidth = compose.onNodeWithTag("order-anchor").fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithText("不加入购买批次").performClick()
        val optionWidth = compose.onNodeWithText("123（CNY）").fetchSemanticsNode().boundsInRoot.width
        assertEquals(orderWidth, optionWidth, 1f)
        capture("layout-order-dropdown")
        compose.onNodeWithText("123（CNY）").performClick()
        assertEquals(1L, selected.value)
        compose.onNodeWithText("123").performClick()
        compose.onNodeWithText("不加入购买批次").performClick()
        assertNull(selected.value)
        compose.onNodeWithContentDescription("选择店铺/渠道").performClick()
        val shopWidth = compose.onNodeWithTag("shop-anchor").fetchSemanticsNode().boundsInRoot.width
        val shopOptionWidth = compose.onNodeWithText("参").fetchSemanticsNode().boundsInRoot.width
        assertEquals(shopWidth, shopOptionWidth, 1f)
        assertEquals(shopOptionWidth, optionWidth, 1f)
        compose.onNodeWithText("参").performClick()
        assertEquals("参", shop.value)
    }

    @Test fun `大字体长批次名称菜单等宽且完整内容可选择`() {
        val title = "骏河屋与转运仓联合购买的十月实体收藏批次"
        val orders = listOf(OrderSummary(PurchaseOrder(1, title, null, null, "JPY", null, 0, 0), 0, emptyMap(), emptyMap()))
        val selected = mutableStateOf<Long?>(null)
        show(1.5f) {
            Column(Modifier.padding(24.dp)) {
                OrderSelector(orders, selected.value, { selected.value = it }, Modifier.testTag("order-anchor"))
            }
        }
        compose.onNodeWithText("不加入购买批次").performClick()
        val item = compose.onNodeWithText("$title（JPY）").assertIsDisplayed()
        val bounds = item.fetchSemanticsNode().boundsInRoot
        assertEquals(compose.onNodeWithTag("order-anchor").fetchSemanticsNode().boundsInRoot.width, bounds.width, 1f)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("$title（JPY）", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().lineCount > 1)
        assertFalse(layouts.single().hasVisualOverflow)
        capture("layout-order-dropdown-large")
        item.performClick()
        assertEquals(1L, selected.value)
    }
}
