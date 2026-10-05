package com.vnventory.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.data.backup.BackupData
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.repository.SettingsRepository
import com.vnventory.app.data.repository.PurchaseRepository
import com.vnventory.app.data.repository.CollectionRepository
import com.vnventory.app.ui.preview.ShelfPreviewData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import com.vnventory.app.domain.cost.CostEngine
import com.vnventory.app.domain.model.*
import com.vnventory.app.domain.text.Message
import com.vnventory.app.ui.orders.*
import com.vnventory.app.ui.settings.BackupImportDialogs
import com.vnventory.app.ui.settings.BackupUiState
import com.vnventory.app.ui.theme.VNventoryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiReviewRegressionTest {
    @get:Rule val compose = createComposeRule()

    private fun show(scale: Float = 1.5f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                VNventoryTheme { Surface { Box(Modifier.fillMaxSize()) { content() } } }
            }
        }
    }

    private fun capture(name: String, dialog: Boolean = false) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val folder = File(checkNotNull(System.getProperty("vnventory.screenshot.dir"))).apply { mkdirs() }
        val node = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        File(folder, "$name.png").outputStream().use {
            assertTrue(node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun `完整恢复预览在小屏大字体下可滚动到配置选项和错误`() {
        val backup = BackupData(0, "JPY", emptyList(), emptyList(), emptyList(), emptyList(),
            shopChannels = listOf("駿河屋", "メルカリ"), appearance = AppearancePreferences(ThemeMode.DARK, true))
        val state = mutableStateOf(BackupUiState(pendingImport = backup))
        var appended = false
        show {
            BackupImportDialogs(state.value, { state.value = state.value.copy(restoreCurrency = it) }, {},
                { appended = true }, {}, {}, {}, error = Message.Literal("恢复失败，请重试"),
                onRestoreShops = { state.value = state.value.copy(restoreShops = it) })
        }
        compose.onNodeWithText("同时恢复店铺/渠道列表").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(isToggleable())[1].performClick()
        assertFalse(state.value.restoreShops)
        compose.onNodeWithText("恢复失败，请重试").performScrollTo().assertIsDisplayed()
        capture("review-backup-dialog-large", dialog = true)
        compose.onNodeWithText("追加恢复").assertIsDisplayed().performClick()
        assertTrue(appended)
    }

    @Test fun `批次实际新建弹窗在小屏大字体下备注与按钮可达`() {
        val state = mutableStateOf(OrdersUiState(createOpen = true, shopChannels = listOf("测试渠道")))
        var cancelled = false
        show {
            OrderCreateDialog(state.value,
                { state.value = state.value.copy(form = state.value.form.copy(title = it)) },
                { state.value = state.value.copy(form = state.value.form.copy(merchant = it)) },
                { state.value = state.value.copy(form = state.value.form.copy(date = it)) },
                { state.value = state.value.copy(form = state.value.form.copy(currency = it)) },
                { state.value = state.value.copy(form = state.value.form.copy(notes = it)) },
                { cancelled = true }, {})
        }
        compose.onNodeWithText("名称，如：骏河屋九月购入").performScrollTo().performTextReplacement("检查批次")
        compose.onNodeWithContentDescription("选择店铺/渠道").performScrollTo().performClick()
        compose.onNodeWithText("测试渠道").performClick()
        compose.runOnIdle {
            assertEquals("测试渠道", state.value.form.merchant)
            state.value = state.value.copy(shopChannels = emptyList())
        }
        compose.onNodeWithText("测试渠道").assertIsDisplayed()
        compose.onNodeWithText("备注（可空）").performScrollTo().performTextReplacement("保留输入")
        compose.runOnIdle { assertEquals("保留输入", state.value.form.notes) }
        compose.onNodeWithText("创建").assertIsDisplayed().assertIsEnabled()
        capture("review-order-dialog-large", dialog = true)
        compose.onNodeWithText("取消").assertIsDisplayed().performClick()
        assertTrue(cancelled)
    }

    @Test fun `编辑固定费用改类型后不显示枚举ID`() {
        val order = PurchaseOrder(1, "Batch", null, null, "JPY", null, 0, 0)
        val expense = Expense(1, 1, ExpenseCategory.INTERNATIONAL_SHIPPING.name, ExpenseCategory.INTERNATIONAL_SHIPPING,
            100, "JPY", AllocationMode.EQUAL, null, 0)
        val detail = OrderDetail(order, emptyList(), listOf(expense), CostEngine.computeOrderCosts(emptyList(), listOf(expense.costInput())))
        val editor = mutableStateOf(ExpenseEditorState(open = true, editingId = expense.id, name = expense.name,
            category = expense.category, amountText = "100", currency = "JPY"))
        show(scale = 1f) {
            ExpenseEditorContent(OrderDetailUiState(loading = false, detail = detail, editor = editor.value),
                { editor.value = editor.value.copy(category = it) }, {}, {}, {}, { _, _ -> })
        }
        compose.onNodeWithText("国内运费").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("INTERNATIONAL_SHIPPING").assertDoesNotExist()
    }

    @Test fun `旧自定义费用名称在编辑页仍然可见`() {
        val order = PurchaseOrder(1, "Batch", null, null, "JPY", null, 0, 0)
        val expense = Expense(1, 1, "历史包装附加费", ExpenseCategory.OTHER, 100, "JPY", AllocationMode.EQUAL, null, 0)
        val detail = OrderDetail(order, emptyList(), listOf(expense), CostEngine.computeOrderCosts(emptyList(), listOf(expense.costInput())))
        val editor = ExpenseEditorState(open = true, editingId = 1, name = expense.name, category = expense.category, amountText = "100", currency = "JPY")
        show(scale = 1f) {
            ExpenseEditorContent(OrderDetailUiState(loading = false, detail = detail, editor = editor), {}, {}, {}, {}, { _, _ -> })
        }
        compose.onNodeWithText("历史包装附加费").assertIsDisplayed()
        compose.onNodeWithText("其他").assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w393dp-h900dp-port-xxhdpi")
    fun `删除被引用渠道后打开含商品和费用的订单仍保留快照并可操作`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, VNventoryDatabase::class.java).allowMainThreadQueries().build()
        val settingsJob = SupervisorJob()
        val settingsScope = CoroutineScope(settingsJob + Dispatchers.IO)
        val file = File(context.cacheDir, "review-${UUID.randomUUID()}.preferences_pb")
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = settingsScope) { file })
        val purchases = PurchaseRepository(db, db.purchaseOrderDao(), db.expenseDao(), db.ownedCopyDao(), Dispatchers.Unconfined)
        val collection = CollectionRepository(db, db.ownedCopyDao(), db.vnCacheDao(), db.expenseDao(), Dispatchers.Unconfined)
        try {
            val detail = runBlocking {
                settings.addShopChannel("复现渠道")
                val merchant = settings.shopChannels.first().single()
                val orderId = purchases.createOrder(PurchaseOrder(0, "渠道订单", merchant, null, "JPY", null, 0, 0))
                collection.addCopies(listOf(ShelfPreviewData.copies.first().copy(id = 0, releaseId = null, orderId = orderId, shop = merchant)))
                purchases.addExpense(Expense(0, orderId, ExpenseCategory.INTERNATIONAL_SHIPPING.name, ExpenseCategory.INTERNATIONAL_SHIPPING, 100, "JPY", AllocationMode.EQUAL, null, 0))
                settings.removeShopChannel(merchant)
                assertTrue(settings.shopChannels.first().isEmpty())
                purchases.observeOrderDetail(orderId).first()!!
            }
            assertEquals("复现渠道", detail.order.merchant)
            assertEquals("复现渠道", detail.copies.single().shop)
            assertEquals(detail.copies.single().id, detail.expenses.single().id)
            var selectedCopy: Long? = null
            var selectedExpense: Long? = null
            show(scale = 1f) { OrderDetailContent(detail, {}, { selectedCopy = it }, {}, { selectedExpense = it.id }, {}, {}) }
            compose.onNodeWithText("复现渠道").assertIsDisplayed()
            compose.onNodeWithText(detail.copies.single().vnTitle).performScrollTo().performClick()
            compose.onNodeWithText("国际运费").performScrollTo().performClick()
            assertEquals(detail.copies.single().id, selectedCopy)
            assertEquals(detail.expenses.single().id, selectedExpense)
            capture("review-order-after-channel-deleted")
        } finally {
            runBlocking { settingsJob.cancelAndJoin() }
            db.close()
        }
    }
}
