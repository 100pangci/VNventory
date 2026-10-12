package com.vnventory.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.local.entity.ExpenseEntity
import com.vnventory.app.data.local.entity.OwnedCopyEntity
import com.vnventory.app.data.local.entity.PurchaseOrderEntity
import com.vnventory.app.data.remote.fx.FxFetchResult
import com.vnventory.app.data.remote.fx.FxRateClient
import com.vnventory.app.data.remote.fx.FxRateSource
import com.vnventory.app.data.repository.CollectionRepository
import com.vnventory.app.data.repository.FxFreezeOutcome
import com.vnventory.app.data.repository.FxFreezer
import com.vnventory.app.data.repository.SettingsRepository
import com.vnventory.app.domain.cost.BudgetRecordKind
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.ExpenseCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FxFreezerTest {

    private lateinit var db: VNventoryDatabase
    private val today = LocalDate.of(2026, 10, 13)
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, VNventoryDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() {
        db.close()
    }

    private class FakeSource(private val result: (String, String, LocalDate) -> FxFetchResult) : FxRateSource {
        val calls = mutableListOf<Triple<String, String, LocalDate>>()
        override val name = "fake"
        override suspend fun fetch(from: String, to: String, date: LocalDate): FxFetchResult {
            calls += Triple(from, to, date)
            return result(from, to, date)
        }
    }

    private fun TestScope.settings(): SettingsRepository = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb") },
    )

    private fun TestScope.freezer(settings: SettingsRepository, source: FxRateSource, scope: CoroutineScope) = FxFreezer(
        database = db,
        settingsRepository = settings,
        client = FxRateClient(listOf(source), today = { today }),
        scope = scope,
        io = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
        today = { today },
    )

    private suspend fun insertCopy(
        priceMinor: Long? = 12_800,
        currency: String = "JPY",
        purchaseDate: LocalDate? = LocalDate.of(2026, 10, 9),
        orderId: Long? = null,
    ): Long = db.ownedCopyDao().insert(
        OwnedCopyEntity(
            vnId = "v1", releaseId = null, vnTitle = "A", releaseTitle = "Manual", coverUrl = null,
            priceMinor = priceMinor, currency = currency, condition = CopyCondition.USED, conditionNote = null,
            purchaseDate = purchaseDate, shop = null, orderId = orderId, notes = null, createdAt = 0, updatedAt = 0,
        )
    )

    private suspend fun insertOrder(orderDate: LocalDate?): Long = db.purchaseOrderDao().insert(
        PurchaseOrderEntity(title = "Batch", merchant = null, orderDate = orderDate, currency = "JPY", notes = null, createdAt = 0, updatedAt = 0)
    )

    private suspend fun insertExpense(orderId: Long, currency: String = "JPY"): Long = db.expenseDao().insert(
        ExpenseEntity(orderId = orderId, name = "Ship", category = ExpenseCategory.SHIPPING, amountMinor = 500, currency = currency, mode = AllocationMode.EQUAL, notes = null, createdAt = 0)
    )

    @Test fun `保存后调用请求路径可按购买日期定格`() = runTest {
        val settings = settings()
        val source = FakeSource { from, to, date ->
            assertEquals(Triple("JPY", "CNY", LocalDate.of(2026, 10, 9)), Triple(from, to, date))
            FxFetchResult.Success(42_299_852L, date, "fake")
        }
        val target = freezer(settings, source, backgroundScope)
        val copyId = insertCopy()
        target.requestCopiesFreeze(listOf(copyId))?.join()
        val copy = db.ownedCopyDao().getById(copyId)!!
        assertEquals(42_299_852L, copy.fxRateScaled)
        assertEquals(LocalDate.of(2026, 10, 9), copy.fxDate)
        assertEquals("CNY", copy.fxToCurrency)
        assertEquals("fake", copy.fxSource)
    }

    @Test fun `同币种与已定格记录不会重复请求`() = runTest {
        val settings = settings()
        val source = FakeSource { _, _, date -> FxFetchResult.Success(50_000_000L, date, "fake") }
        val target = freezer(settings, source, backgroundScope)
        val cny = insertCopy(currency = "CNY", purchaseDate = LocalDate.of(2026, 10, 9))
        val jpy = insertCopy()
        target.freezeRecord(BudgetRecordKind.COPY, cny)
        assertEquals(FxFreezeOutcome.SKIPPED, target.freezeRecord(BudgetRecordKind.COPY, cny))
        assertTrue(source.calls.isEmpty())
        assertEquals(FxFreezeOutcome.FROZEN, target.freezeRecord(BudgetRecordKind.COPY, jpy))
        assertEquals(FxFreezeOutcome.SKIPPED, target.freezeRecord(BudgetRecordKind.COPY, jpy))
        assertEquals(1, source.calls.size)
    }

    @Test fun `显式重新获取可刷新同目标币种快照`() = runTest {
        val settings = settings()
        var rate = 42_299_852L
        val source = FakeSource { _, _, date -> FxFetchResult.Success(rate, date, "fake") }
        val target = freezer(settings, source, backgroundScope)
        val copyId = insertCopy()

        assertEquals(FxFreezeOutcome.FROZEN, target.freezeRecord(BudgetRecordKind.COPY, copyId))
        assertEquals(42_299_852L, db.ownedCopyDao().getById(copyId)!!.fxRateScaled)

        rate = 43_000_000L
        assertEquals(
            FxFreezeOutcome.FROZEN,
            target.freezeRecord(BudgetRecordKind.COPY, copyId, includeStale = true, forceRefresh = true),
        )
        assertEquals(43_000_000L, db.ownedCopyDao().getById(copyId)!!.fxRateScaled)
        assertEquals(2, source.calls.size)
    }

    @Test fun `自动重试只覆盖最近三天且不请求旧记录`() = runTest {
        val settings = settings()
        val source = FakeSource { _, _, date -> FxFetchResult.Success(42_299_852L, date, "fake") }
        val target = freezer(settings, source, backgroundScope)
        val recent = insertCopy(purchaseDate = LocalDate.of(2026, 10, 12))
        val old = insertCopy(purchaseDate = LocalDate.of(2026, 10, 3))
        target.requestRecentRetry().join()
        assertEquals(listOf(Triple("JPY", "CNY", LocalDate.of(2026, 10, 12))), source.calls)
        assertNotNull(db.ownedCopyDao().getById(recent)!!.fxRateScaled)
        assertNull(db.ownedCopyDao().getById(old)!!.fxRateScaled)
        // 显式补全才请求旧记录。
        target.backfillAll()
        assertEquals(2, source.calls.size)
        assertNotNull(db.ownedCopyDao().getById(old)!!.fxRateScaled)
    }

    @Test fun `尚未发布保持待定格并计入统计`() = runTest {
        val settings = settings()
        val source = FakeSource { _, _, _ -> FxFetchResult.NotAvailable }
        val target = freezer(settings, source, backgroundScope)
        val copyId = insertCopy(purchaseDate = today)
        val result = target.backfillAll()
        assertEquals(1, result.pending)
        assertEquals(0, result.succeeded)
        assertNull(db.ownedCopyDao().getById(copyId)!!.fxRateScaled)
    }

    @Test fun `费用按订单日期定格`() = runTest {
        val settings = settings()
        val source = FakeSource { from, to, date ->
            assertEquals(Triple("JPY", "CNY", LocalDate.of(2026, 9, 30)), Triple(from, to, date))
            FxFetchResult.Success(42_299_852L, date, "fake")
        }
        val target = freezer(settings, source, backgroundScope)
        val orderId = insertOrder(LocalDate.of(2026, 9, 30))
        val expenseId = insertExpense(orderId)
        assertEquals(FxFreezeOutcome.FROZEN, target.freezeRecord(BudgetRecordKind.EXPENSE, expenseId))
        assertEquals("CNY", db.expenseDao().getById(expenseId)!!.fxToCurrency)
    }

    @Test fun `手动定格与清除`() = runTest {
        val settings = settings()
        val source = FakeSource { _, _, _ -> FxFetchResult.Failed }
        val target = freezer(settings, source, backgroundScope)
        val copyId = insertCopy()
        target.setManualSnapshot(BudgetRecordKind.COPY, copyId, 42_300_000L, LocalDate.of(2026, 10, 10))
        val frozen = db.ownedCopyDao().getById(copyId)!!
        assertEquals("manual", frozen.fxSource)
        assertEquals(42_300_000L, frozen.fxRateScaled)
        target.clearSnapshot(BudgetRecordKind.COPY, copyId)
        assertNull(db.ownedCopyDao().getById(copyId)!!.fxRateScaled)
        assertTrue(source.calls.isEmpty())
    }

    @Test fun `结算币种变化后按原日期重新定格`() = runTest {
        val settings = settings()
        val source = FakeSource { _, _, date -> FxFetchResult.Success(42_299_852L, date, "fake") }
        val target = freezer(settings, source, backgroundScope)
        val copyId = insertCopy()
        target.freezeRecord(BudgetRecordKind.COPY, copyId)
        settings.setBudgetCurrency("USD")
        val result = target.backfillAll()
        assertEquals(1, result.succeeded)
        assertEquals(Triple("JPY", "USD", LocalDate.of(2026, 10, 9)), source.calls.last())
        assertEquals("USD", db.ownedCopyDao().getById(copyId)!!.fxToCurrency)
    }

    @Test fun `修改币种会清除定格并在保存后可重新获取`() = runTest {
        val settings = settings()
        val source = FakeSource { _, _, date -> FxFetchResult.Success(42_299_852L, date, "fake") }
        val target = freezer(settings, source, backgroundScope)
        val collection = CollectionRepository(db, db.ownedCopyDao(), db.vnCacheDao(), db.expenseDao(), Dispatchers.Unconfined)
        val copyId = insertCopy()
        target.freezeRecord(BudgetRecordKind.COPY, copyId)
        assertNotNull(db.ownedCopyDao().getById(copyId)!!.fxRateScaled)
        val copy = collection.getById(copyId)!!
        collection.update(copy.copy(currency = "CNY"))
        val updated = db.ownedCopyDao().getById(copyId)!!
        assertNull(updated.fxRateScaled)
        assertNull(updated.fxToCurrency)
        // 同币种无需定格。
        assertEquals(FxFreezeOutcome.SKIPPED, target.freezeRecord(BudgetRecordKind.COPY, copyId))
    }

    @Test fun `购买日期改变清除自动快照但保留手动快照`() = runTest {
        val settings = settings()
        val source = FakeSource { _, _, date -> FxFetchResult.Success(42_299_852L, date, "fake") }
        val target = freezer(settings, source, backgroundScope)
        val collection = CollectionRepository(db, db.ownedCopyDao(), db.vnCacheDao(), db.expenseDao(), Dispatchers.Unconfined)

        val automaticId = insertCopy(purchaseDate = LocalDate.of(2026, 10, 9))
        target.freezeRecord(BudgetRecordKind.COPY, automaticId)
        val automatic = collection.getById(automaticId)!!
        collection.update(automatic.copy(purchaseDate = LocalDate.of(2026, 10, 10)))
        assertNull(db.ownedCopyDao().getById(automaticId)!!.fxRateScaled)

        val manualId = insertCopy(purchaseDate = LocalDate.of(2026, 10, 9))
        target.setManualSnapshot(BudgetRecordKind.COPY, manualId, 50_000_000L, LocalDate.of(2026, 10, 8))
        val manual = collection.getById(manualId)!!
        collection.update(manual.copy(purchaseDate = LocalDate.of(2026, 10, 10)))
        assertEquals(50_000_000L, db.ownedCopyDao().getById(manualId)!!.fxRateScaled)
        assertEquals(LocalDate.of(2026, 10, 8), db.ownedCopyDao().getById(manualId)!!.fxDate)
    }

    @Test fun `批次日期改变清除依赖批次日期的自动快照`() = runTest {
        val settings = settings()
        val source = FakeSource { _, _, date -> FxFetchResult.Success(42_299_852L, date, "fake") }
        val target = freezer(settings, source, backgroundScope)
        val purchases = com.vnventory.app.data.repository.PurchaseRepository(
            db, db.purchaseOrderDao(), db.expenseDao(), db.ownedCopyDao(), Dispatchers.Unconfined,
        )
        val orderId = insertOrder(LocalDate.of(2026, 10, 9))
        val fallbackCopyId = insertCopy(purchaseDate = null, orderId = orderId)
        val explicitDateCopyId = insertCopy(purchaseDate = LocalDate.of(2026, 10, 8), orderId = orderId)
        val expenseId = insertExpense(orderId)
        target.freezeRecord(BudgetRecordKind.COPY, fallbackCopyId)
        target.freezeRecord(BudgetRecordKind.COPY, explicitDateCopyId)
        target.freezeRecord(BudgetRecordKind.EXPENSE, expenseId)

        val oldOrder = purchases.getOrder(orderId)!!
        purchases.updateOrder(oldOrder.copy(orderDate = LocalDate.of(2026, 10, 10)))

        assertNull(db.ownedCopyDao().getById(fallbackCopyId)!!.fxRateScaled)
        assertNull(db.expenseDao().getById(expenseId)!!.fxRateScaled)
        // 有独立购买日期的收藏不依赖订单日期，快照保持不变。
        assertNotNull(db.ownedCopyDao().getById(explicitDateCopyId)!!.fxRateScaled)

        target.requestCopiesFreeze(listOf(fallbackCopyId))?.join()
        target.requestExpenseFreeze(expenseId)?.join()
        assertEquals(LocalDate.of(2026, 10, 10), db.ownedCopyDao().getById(fallbackCopyId)!!.fxDate)
        assertEquals(LocalDate.of(2026, 10, 10), db.expenseDao().getById(expenseId)!!.fxDate)
    }
}
