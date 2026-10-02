package com.vnventory.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.repository.CollectionRepository
import com.vnventory.app.data.repository.PurchaseRepository
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.CollectionQuery
import com.vnventory.app.domain.model.CollectionSort
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.Expense
import com.vnventory.app.domain.model.ExpenseCategory
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.domain.model.PurchaseOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 数据层集成测试（Robolectric + Room 内存数据库）。
 * 覆盖：查询/搜索/排序、订单删除语义、手动分摊的生命周期与池化成本计算。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataLayerTest {

    private lateinit var db: VNventoryDatabase
    private lateinit var collection: CollectionRepository
    private lateinit var purchases: PurchaseRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VNventoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        collection = CollectionRepository(
            database = db,
            ownedCopyDao = db.ownedCopyDao(),
            vnCacheDao = db.vnCacheDao(),
            expenseDao = db.expenseDao(),
            io = Dispatchers.Unconfined,
        )
        purchases = PurchaseRepository(
            database = db,
            orderDao = db.purchaseOrderDao(),
            expenseDao = db.expenseDao(),
            ownedCopyDao = db.ownedCopyDao(),
            io = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---- 基础查询 ----

    @Test
    fun `添加收藏并观察计数与总价`() = runTest {
        collection.addCopies(
            listOf(
                copy(price = 5000),
                copy(price = 3000, vnId = "v3", vnTitle = "Utawarerumono"),
            )
        )
        assertEquals(2, collection.observeCopyCount().first())
        assertEquals(2, collection.observeDistinctVnCount().first())
        val totals = collection.observePriceTotals().first()
        assertEquals(8000L, totals.first { it.currency == "JPY" }.total)
    }

    @Test
    fun `搜索与排序`() = runTest {
        collection.addCopies(
            listOf(
                copy(vnTitle = "Beta Game", price = 100),
                copy(vnTitle = "Alpha Game", price = 300),
                copy(vnTitle = "Gamma", price = 200, shop = "Amazon"),
            )
        )

        val byTitle = collection.observeCollection(CollectionQuery(sort = CollectionSort.TITLE_ASC)).first()
        assertEquals(listOf("Alpha Game", "Beta Game", "Gamma"), byTitle.map { it.vnTitle })

        val byPriceDesc = collection.observeCollection(CollectionQuery(sort = CollectionSort.PRICE_DESC)).first()
        assertEquals(listOf(300L, 200L, 100L), byPriceDesc.map { it.priceMinor })

        val searchShop = collection.observeCollection(CollectionQuery(search = "Amazon")).first()
        assertEquals(1, searchShop.size)
        assertEquals("Gamma", searchShop[0].vnTitle)

        val searchTitle = collection.observeCollection(CollectionQuery(search = "Game")).first()
        assertEquals(2, searchTitle.size)
    }

    // ---- 订单语义 ----

    @Test
    fun `删除订单时收藏保留 费用级联删除`() = runTest {
        val orderId = purchases.createOrder(order("测试批次"))
        collection.addCopies(listOf(copy(orderId = orderId), copy(orderId = orderId)))
        purchases.addExpense(expense(orderId, name = "运费", amount = 100, category = ExpenseCategory.SHIPPING))

        assertEquals(2, purchases.countCopiesInOrder(orderId))

        purchases.deleteOrder(orderId)

        val remaining = collection.observeCollection(CollectionQuery()).first()
        assertEquals(2, remaining.size)
        assertTrue(remaining.all { it.orderId == null })
        assertNull(purchases.observeOrderDetail(orderId).first())
    }

    @Test
    fun `手动分摊随盒子移出订单而清理`() = runTest {
        val orderId = purchases.createOrder(order("批次"))
        val ids = collection.addCopies(listOf(copy(orderId = orderId), copy(orderId = orderId)))
        purchases.addExpense(
            expense(
                orderId,
                name = "国际运费",
                amount = 200,
                mode = AllocationMode.MANUAL,
                manual = mapOf(ids[0] to 120L, ids[1] to 80L),
            )
        )

        purchases.assignCopiesToOrder(listOf(ids[0]), orderId = null)

        val detail = purchases.observeOrderDetail(orderId).first()!!
        val saved = detail.expenses.single()
        assertEquals(mapOf(ids[1] to 80L), saved.allocations)
        assertEquals(5080L, detail.costFor(ids[1])!!.totalsByCurrency["JPY"])
    }

    @Test
    fun `删除盒子级联清理手动分摊`() = runTest {
        val orderId = purchases.createOrder(order("批次"))
        val ids = collection.addCopies(listOf(copy(orderId = orderId), copy(orderId = orderId)))
        purchases.addExpense(
            expense(
                orderId,
                name = "运费",
                amount = 100,
                mode = AllocationMode.MANUAL,
                manual = mapOf(ids[0] to 50L, ids[1] to 50L),
            )
        )

        collection.delete(ids[0])

        val detail = purchases.observeOrderDetail(orderId).first()!!
        assertEquals(mapOf(ids[1] to 50L), detail.expenses.single().allocations)
    }

    @Test
    fun `费用改为自动模式会清空手动分摊行`() = runTest {
        val orderId = purchases.createOrder(order("批次"))
        val ids = collection.addCopies(listOf(copy(orderId = orderId)))
        val expenseId = purchases.addExpense(
            expense(
                orderId,
                name = "运费",
                amount = 100,
                mode = AllocationMode.MANUAL,
                manual = mapOf(ids[0] to 100L),
            )
        )

        val saved = purchases.getExpense(expenseId)!!
        purchases.updateExpense(saved.copy(mode = AllocationMode.EQUAL, allocations = emptyMap()))

        val detail = purchases.observeOrderDetail(orderId).first()!!
        assertEquals(emptyMap<Long, Long>(), detail.expenses.single().allocations)
        assertEquals(5100L, detail.costFor(ids[0])!!.totalsByCurrency["JPY"])
    }

    @Test
    fun `订单详情实时计算池化分摊`() = runTest {
        val orderId = purchases.createOrder(order("批次"))
        val ids = collection.addCopies(
            listOf(
                copy(price = 50, orderId = orderId),
                copy(price = 60, orderId = orderId),
                copy(price = 70, orderId = orderId),
            )
        )
        purchases.addExpense(expense(orderId, name = "支付手续费", amount = 2, category = ExpenseCategory.FEE))
        purchases.addExpense(expense(orderId, name = "国际运费", amount = 100, category = ExpenseCategory.SHIPPING))

        val detail = purchases.observeOrderDetail(orderId).first()!!
        assertEquals(84L, detail.costFor(ids[0])!!.totalsByCurrency["JPY"])
        assertEquals(94L, detail.costFor(ids[1])!!.totalsByCurrency["JPY"])
        assertEquals(104L, detail.costFor(ids[2])!!.totalsByCurrency["JPY"])
        assertEquals(282L, detail.breakdown.totalsByCurrency["JPY"])
    }

    // ---- helpers ----

    private fun copy(
        vnId: String = "v17",
        vnTitle: String = "Ever17",
        releaseId: String? = "r17",
        releaseTitle: String? = "初回版",
        price: Long = 5000,
        currency: String = "JPY",
        date: LocalDate? = LocalDate.of(2026, 9, 1),
        shop: String? = "骏河屋",
        orderId: Long? = null,
    ) = OwnedCopy(
        id = 0,
        vnId = vnId,
        releaseId = releaseId,
        vnTitle = vnTitle,
        releaseTitle = releaseTitle,
        coverUrl = null,
        priceMinor = price,
        currency = currency,
        condition = CopyCondition.USED,
        conditionNote = null,
        purchaseDate = date,
        shop = shop,
        orderId = orderId,
        notes = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun order(title: String) = PurchaseOrder(
        id = 0,
        title = title,
        merchant = null,
        orderDate = LocalDate.of(2026, 9, 1),
        currency = "JPY",
        notes = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun expense(
        orderId: Long,
        name: String,
        amount: Long,
        currency: String = "JPY",
        mode: AllocationMode = AllocationMode.EQUAL,
        category: ExpenseCategory = ExpenseCategory.OTHER,
        manual: Map<Long, Long> = emptyMap(),
    ) = Expense(
        id = 0,
        orderId = orderId,
        name = name,
        category = category,
        amountMinor = amount,
        currency = currency,
        mode = mode,
        notes = null,
        createdAt = 1L,
        allocations = manual,
    )
}
