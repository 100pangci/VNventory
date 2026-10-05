package com.vnventory.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vnventory.app.domain.cost.CostEngine
import com.vnventory.app.domain.model.*
import com.vnventory.app.ui.components.FormSaveBar
import com.vnventory.app.ui.components.MoneyInputField
import com.vnventory.app.ui.edit.CopyEditContent
import com.vnventory.app.ui.edit.CopyEditUiState
import com.vnventory.app.ui.edit.EditFormState
import com.vnventory.app.ui.orders.ExpenseEditorContent
import com.vnventory.app.ui.orders.ExpenseEditorState
import com.vnventory.app.ui.orders.OrderDetailUiState
import com.vnventory.app.ui.preview.ShelfPreviewData
import com.vnventory.app.ui.settings.SettingsDetailScaffold
import com.vnventory.app.ui.settings.SettingsHomeContent
import com.vnventory.app.ui.settings.SettingsPreferencesContent
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
class FormsSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(scale: Float = 1f, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                VNventoryTheme(darkTheme = dark) { Surface { Box(Modifier.fillMaxSize()) { content() } } }
            }
        }
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val folder = File(checkNotNull(System.getProperty("vnventory.screenshot.dir"))).apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun `窄屏大字体金额不被币种挤压且两者都可编辑`() {
        val amount = mutableStateOf("")
        val currency = mutableStateOf("CNY")
        show(scale = 1.5f) {
            Column(Modifier.padding(24.dp)) {
                MoneyInputField(amount.value, currency.value, "单盒价格", { amount.value = it }, { currency.value = it }, supportingText = "选填，留空表示未记录价格")
            }
        }
        compose.onNodeWithText("单盒价格").performTextReplacement("12345.67")
        compose.onNodeWithText("CNY").assertIsDisplayed().performClick()
        compose.onNodeWithText("JPY（¥）").performClick()
        assertEquals("JPY", currency.value)
        assertEquals("12345.67", amount.value)
        val input = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val selector = compose.onNodeWithText("JPY").fetchSemanticsNode().boundsInRoot
        assertTrue("Currency must be below the amount on narrow layouts", selector.top >= input.bottom)
        capture("money-field-large")
    }

    @Test fun `编辑表单分组保留手动绑定且空价格与零价仍区分`() {
        val copy = ShelfPreviewData.copies.last()
        val form = mutableStateOf(EditFormState(releaseTitle = copy.releaseTitle!!, currency = "JPY"))
        var bound = false
        show {
            CopyEditContent(CopyEditUiState(loading = false, copy = copy, form = form.value), { form.value = it }, { bound = true })
        }
        compose.onNodeWithText("绑定到 VNDB 版本").performScrollTo().performClick()
        assertTrue(bound)
        compose.onNodeWithText("手动版本名称").performTextReplacement("店铺特典版")
        compose.onNodeWithText("单盒价格").performScrollTo().performTextReplacement("0")
        assertEquals(0L, form.value.parsedPrice)
        compose.onNodeWithText("单盒价格").performTextReplacement("")
        assertNull(form.value.parsedPrice)
        assertTrue(form.value.canSave)
        capture("copy-edit-grouped")
    }

    @Test fun `大字体编辑品相全部可选且自定义说明不藏在横向滚动中`() {
        val copy = ShelfPreviewData.copies.first()
        val form = mutableStateOf(EditFormState(releaseTitle = copy.releaseTitle!!, currency = "JPY"))
        show(scale = 1.5f, dark = true) {
            CopyEditContent(CopyEditUiState(loading = false, copy = copy, form = form.value), { form.value = it }, {})
        }
        compose.onNodeWithText("自定义").performScrollTo().performClick()
        compose.onNodeWithText("自定义品相说明").performScrollTo().performTextReplacement("外盒轻微磨损")
        assertEquals(CopyCondition.CUSTOM, form.value.condition)
        assertEquals("外盒轻微磨损", form.value.conditionNote)
        capture("copy-edit-large-dark")
        compose.onNodeWithText("所属购买批次").performScrollTo().assertIsDisplayed()
    }

    @Test fun `费用类型换行保留五种选择与未知价格比例保护和手动输入`() {
        val copies = listOf(ShelfPreviewData.copies.first().copy(priceMinor = null))
        val detail = OrderDetail(PurchaseOrder(1, "Batch", null, null, "JPY", null, 0, 0), copies, emptyList(), CostEngine.computeOrderCosts(copies.map { it.costInput() }, emptyList()))
        val editor = mutableStateOf(ExpenseEditorState(open = true, amountText = "100", currency = "JPY"))
        show(scale = 1.5f) {
            ExpenseEditorContent(OrderDetailUiState(loading = false, detail = detail, editor = editor.value),
                { editor.value = editor.value.copy(category = it) }, { editor.value = editor.value.copy(amountText = it) },
                { editor.value = editor.value.copy(currency = it) }, { editor.value = editor.value.copy(mode = it) },
                { id, text -> editor.value = editor.value.copy(manualInputs = editor.value.manualInputs + (id to text)) })
        }
        for (label in listOf("国际运费", "岛内运费", "国内运费", "手续费", "税费")) {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("税费").performClick()
        assertEquals(ExpenseCategory.TAX, editor.value.category)
        compose.onNodeWithText("按价格比例").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("手动指定").performClick()
        compose.onNodeWithTag("manual-allocation-${copies.single().id}").performScrollTo().performTextReplacement("40")
        assertEquals(mapOf(copies.single().id to 40L), editor.value.parsedManual)
        capture("expense-form-large")
    }

    @Test fun `设置分组和偏好页大字体下开关货币入口全部可达`() {
        val appearance = mutableStateOf(AppearancePreferences())
        val shelf = mutableStateOf(false)
        val stats = mutableStateOf(false)
        val releaseNames = mutableStateOf(false)
        val currency = mutableStateOf("JPY")
        show(scale = 1.5f, dark = true) {
            SettingsDetailScaffold("偏好设置", {}) { padding ->
                SettingsPreferencesContent(currency.value, appearance.value, shelf.value, stats.value, releaseNames.value,
                    { appearance.value = appearance.value.copy(themeMode = it) }, { appearance.value = appearance.value.copy(dynamicColor = it) },
                    { shelf.value = it }, { stats.value = it }, { releaseNames.value = it }, { currency.value = it }, Modifier.padding(padding))
            }
        }
        compose.onNodeWithText("深色").performScrollTo().performClick().assertIsSelected()
        capture("settings-preferences-large-dark")
        compose.onNodeWithText("在书架显示价格").performScrollTo().performClick()
        compose.onNodeWithText("显示价格统计").performScrollTo().performClick()
        assertTrue(shelf.value)
        assertTrue(stats.value)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("显示版本名"))
        compose.onNodeWithText("显示版本名").performClick()
        assertTrue(releaseNames.value)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("CNY（¥）"))
        compose.onNodeWithText("CNY（¥）").performClick().assertIsSelected()
        assertEquals("CNY", currency.value)
    }

    @Test fun `设置首页紧凑分组保持四个入口`() {
        var clicks = 0
        show { SettingsHomeContent("JPY", { clicks++ }, { clicks++ }, { clicks++ }, onShops = { clicks++ }) }
        for (label in listOf("偏好设置", "店铺 / 渠道", "备份与恢复", "关于与数据来源")) compose.onNodeWithText(label).performScrollTo().performClick()
        assertEquals(4, clicks)
        capture("settings-grouped-home")
    }

    @Test fun `保存区允许长按钮随大字体增高而不是裁掉文字`() {
        var saved = false
        val label = "保存这盒的修改并更新购买批次成本"
        show(scale = 1.8f) {
            Column(Modifier.width(260.dp)) { FormSaveBar(label, false, true, { saved = true }) }
        }
        compose.onNodeWithText(label).assertIsDisplayed().performClick()
        assertTrue(saved)
        val height = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.height
        val density = compose.density.density
        assertTrue(height > 56f * density)
        capture("form-save-large")
    }
}
