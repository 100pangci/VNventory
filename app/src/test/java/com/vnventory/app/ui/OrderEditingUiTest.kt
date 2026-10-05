package com.vnventory.app.ui

import android.content.Context
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
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.repository.CollectionRepository
import com.vnventory.app.data.repository.PurchaseRepository
import com.vnventory.app.domain.model.*
import com.vnventory.app.domain.text.MessageException
import com.vnventory.app.ui.orders.*
import com.vnventory.app.ui.preview.ShelfPreviewData
import com.vnventory.app.ui.theme.VNventoryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OrderEditingUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun database() = Room.inMemoryDatabaseBuilder(context, VNventoryDatabase::class.java).allowMainThreadQueries().build()
    private fun purchases(db: VNventoryDatabase) = PurchaseRepository(db, db.purchaseOrderDao(), db.expenseDao(), db.ownedCopyDao(), Dispatchers.Unconfined)

    @Test fun `批次更新所有字段保留收藏费用分摊及创建时间`() = runBlocking {
        val db = database()
        try {
            val repo = purchases(db)
            val order = PurchaseOrder(0, "旧批次", "历史渠道", LocalDate.of(2026, 9, 1), "JPY", "旧备注", 100, 100)
            val id = repo.createOrder(order)
            val collection = CollectionRepository(db, db.ownedCopyDao(), db.vnCacheDao(), db.expenseDao(), Dispatchers.Unconfined)
            collection.addCopies(listOf(ShelfPreviewData.copies.first().copy(id = 0, releaseId = null, orderId = id, shop = "历史渠道")))
            val copy = repo.observeOrderDetail(id).first()!!.copies.single()
            repo.addExpense(Expense(0, id, ExpenseCategory.INTERNATIONAL_SHIPPING.name, ExpenseCategory.INTERNATIONAL_SHIPPING,
                100, "JPY", AllocationMode.MANUAL, null, 100, mapOf(copy.id to 100)))
            val before = repo.observeOrderDetail(id).first()!!
            repo.updateOrder(order.copy(id = id, title = "新批次", merchant = "新渠道", orderDate = LocalDate.of(2026, 10, 5),
                currency = "CNY", notes = "新备注", createdAt = 999, updatedAt = 200))
            val after = repo.observeOrderDetail(id).first()!!
            assertEquals("新批次", after.order.title)
            assertEquals("新渠道", after.order.merchant)
            assertEquals(LocalDate.of(2026, 10, 5), after.order.orderDate)
            assertEquals("CNY", after.order.currency)
            assertEquals("新备注", after.order.notes)
            assertEquals(100L, after.order.createdAt)
            assertEquals(200L, after.order.updatedAt)
            assertEquals(before.copies, after.copies)
            assertEquals(before.expenses, after.expenses)
            assertEquals(before.breakdown, after.breakdown)
        } finally { db.close() }
    }

    @Test fun `批次更新拒绝空名称和已删除订单`() = runBlocking {
        val db = database()
        try {
            val repo = purchases(db)
            val order = PurchaseOrder(0, "批次", null, null, "JPY", null, 100, 100)
            val id = repo.createOrder(order)
            try { repo.updateOrder(order.copy(id = id, title = " ")); fail("Blank title must fail") } catch (_: MessageException) { }
            assertEquals("批次", repo.getOrder(id)!!.title)
            repo.deleteOrder(id)
            try { repo.updateOrder(order.copy(id = id)); fail("Deleted order must fail") } catch (_: MessageException) { }
            assertNull(repo.getOrder(id))
        } finally { db.close() }
    }

    @Test fun `实际详情页编辑入口预填取消不写入再次打开可保存`() {
        val db = database()
        val repo = purchases(db)
        val id = runBlocking { repo.createOrder(PurchaseOrder(0, "原批次", "已删除候选的历史渠道", null, "JPY", "原备注", 100, 100)) }
        val store = ViewModelStore()
        val vm = OrderDetailViewModel(repo, id)
        store.put("detail", vm)
        try {
            compose.setContent { VNventoryTheme { Surface { OrderDetailScreen({}, {}, {}, vm) } } }
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("编辑批次").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("编辑批次").performClick()
            compose.onNodeWithText("编辑购买批次").assertIsDisplayed()
            compose.onNodeWithText("名称，如：骏河屋九月购入").performTextReplacement("未保存草稿")
            compose.onNodeWithText("取消").performClick()
            assertEquals("原批次", runBlocking { repo.getOrder(id)!!.title })
            compose.onNodeWithContentDescription("编辑批次").performClick()
            assertEquals("原批次", vm.uiState.value.orderEditor.form.title)
            assertEquals("已删除候选的历史渠道", vm.uiState.value.orderEditor.form.merchant)
            compose.onNodeWithText("名称，如：骏河屋九月购入").performTextReplacement("修改后批次")
            compose.onNodeWithText("商家 / 转运（可空）").performTextReplacement("新渠道")
            compose.onNodeWithText("币种").performScrollTo()
            compose.onNode(hasText("JPY") and hasClickAction()).performClick()
            compose.onNodeWithText("CNY（¥）").performClick()
            compose.onNodeWithText("备注（可空）").performScrollTo().performTextReplacement("新备注")
            compose.runOnIdle {
                assertEquals("修改后批次", vm.uiState.value.orderEditor.form.title)
                assertEquals("新渠道", vm.uiState.value.orderEditor.form.merchant)
                assertEquals("新备注", vm.uiState.value.orderEditor.form.notes)
                vm.onOrderDateChange(LocalDate.of(2026, 10, 5))
            }
            compose.onNodeWithText("保存").assertIsEnabled().performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("编辑购买批次").fetchSemanticsNodes().isEmpty() || vm.actionError.value != null
            }
            assertNull(vm.actionError.value)
            val saved = runBlocking { repo.getOrder(id)!! }
            assertEquals("新渠道", saved.merchant)
            assertEquals("CNY", saved.currency)
            assertEquals("新备注", saved.notes)
            assertEquals(LocalDate.of(2026, 10, 5), saved.orderDate)
            assertEquals(100L, saved.createdAt)
            compose.onNodeWithText("修改后批次").assertIsDisplayed()
        } finally {
            compose.runOnIdle { store.clear() }
            db.close()
        }
    }

    @Test fun `保存已删除批次失败时保留编辑草稿及错误`() {
        val db = database()
        val repo = purchases(db)
        val id = runBlocking { repo.createOrder(PurchaseOrder(0, "原批次", null, null, "JPY", null, 100, 100)) }
        val store = ViewModelStore()
        val vm = OrderDetailViewModel(repo, id)
        store.put("detail", vm)
        try {
            compose.setContent { VNventoryTheme { Surface { OrderDetailScreen({}, {}, {}, vm) } } }
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("编辑批次").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("编辑批次").performClick()
            compose.onNodeWithText("名称，如：骏河屋九月购入").performTextReplacement("保留这个草稿")
            runBlocking { repo.deleteOrder(id) }
            compose.onNodeWithText("保存").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("关闭").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("编辑购买批次").assertIsDisplayed()
            assertEquals("保留这个草稿", vm.uiState.value.orderEditor.form.title)
            assertTrue(vm.uiState.value.orderEditor.open)
            assertFalse(vm.uiState.value.orderEditor.saving)
            assertNotNull(vm.actionError.value)
            assertNull(runBlocking { repo.getOrder(id) })
        } finally {
            compose.runOnIdle { store.clear() }
            db.close()
        }
    }

    @Test fun `小屏大字体编辑弹窗保存期间不可修改或关闭`() {
        val state = mutableStateOf(OrdersUiState(createOpen = true, form = OrderFormState(title = "现有批次", merchant = "历史渠道", notes = "原备注")))
        var saved = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                VNventoryTheme(darkTheme = true) {
                    OrderCreateDialog(state.value, {}, {}, {}, {}, {}, {}, { saved = true }, editing = true)
                }
            }
        }
        compose.onNodeWithText("默认币种仅用于之后的录入，不会转换已有商品或费用的金额与币种。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("保存").assertIsDisplayed().performClick()
        assertTrue(saved)
        val folder = File(checkNotNull(System.getProperty("vnventory.screenshot.dir"))).apply { mkdirs() }
        File(folder, "order-edit-large-dark.png").outputStream().use {
            assertTrue(compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        compose.runOnIdle { state.value = state.value.copy(creating = true) }
        compose.onNodeWithText("取消").assertIsNotEnabled()
        compose.onNodeWithText("正在保存…").assertIsNotEnabled()
        compose.onNodeWithText("商家 / 转运（可空）").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("CNY").performScrollTo().assertIsNotEnabled()
    }
}
