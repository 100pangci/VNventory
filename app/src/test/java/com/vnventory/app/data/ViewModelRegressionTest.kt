package com.vnventory.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.mapper.toDomain
import com.vnventory.app.data.remote.vndb.*
import com.vnventory.app.data.repository.*
import com.vnventory.app.domain.model.*
import com.vnventory.app.ui.add.AddFlowViewModel
import com.vnventory.app.ui.add.AddFlowEvent
import com.vnventory.app.ui.orders.OrderDetailViewModel
import com.vnventory.app.ui.edit.CopyEditViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewModelRegressionTest {
    private lateinit var db: VNventoryDatabase
    private lateinit var collection: CollectionRepository
    private lateinit var purchases: PurchaseRepository
    private val store = ViewModelStore()
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VNventoryDatabase::class.java).allowMainThreadQueries().build()
        collection = CollectionRepository(db, db.ownedCopyDao(), db.vnCacheDao(), db.expenseDao(), Dispatchers.Unconfined)
        purchases = PurchaseRepository(db, db.purchaseOrderDao(), db.expenseDao(), db.ownedCopyDao(), Dispatchers.Unconfined)
    }
    @After fun close() { store.clear(); db.close(); Dispatchers.resetMain() }

    private fun TestScope.addVm(api: VndbService): AddFlowViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb") }
        return AddFlowViewModel(VnRepository(api, db.vnCacheDao(), Dispatchers.Unconfined), collection, purchases, SettingsRepository(dataStore), null)
            .also { store.put("add", it); backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect() } }
    }

    @Test fun `搜索加载下一页去重且失败可重试不丢第一页`() = runTest {
        var failNext = true
        val vm = addVm(object : FakeVndb() {
            override suspend fun searchVn(query: String, page: Int, results: Int): VndbVnResponse {
                if (page == 2 && failNext) { failNext = false; throw java.io.IOException("offline") }
                return if (page == 1) VndbVnResponse(listOf(VndbVnDto("v1")), more = true)
                else VndbVnResponse(listOf(VndbVnDto("v1"), VndbVnDto("v2")))
            }
        })
        vm.onQueryChange("A")
        advanceTimeBy(351)
        vm.uiState.first { it.search.page == 1 }
        vm.loadMore()
        val failed = vm.uiState.first { it.search.error != null }
        assertEquals(listOf("v1"), failed.search.results.map { it.id })
        vm.retrySearch()
        val ready = vm.uiState.first { it.search.page == 2 }
        assertEquals(listOf("v1", "v2"), ready.search.results.map { it.id })
        assertFalse(ready.search.hasMore)
    }

    @Test fun `切换VN后旧响应不可覆盖或造成错绑`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val vm = addVm(object : FakeVndb() {
            override suspend fun getReleases(vnId: String, page: Int, results: Int): VndbReleaseResponse {
                if (vnId == "v1") { entered.complete(Unit); withContext(NonCancellable) { gate.await() } }
                return VndbReleaseResponse(listOf(VndbReleaseDto(if (vnId == "v1") "r1" else "r2", title = vnId)))
            }
        })
        vm.selectVn(VndbVnDto("v1").toDomain())
        entered.await()
        vm.backToSearch()
        vm.selectVn(VndbVnDto("v2").toDomain())
        val ready = vm.uiState.first { it.selectedVn?.id == "v2" && it.releases.releases.any { r -> r.id == "r2" } }
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("r2"), vm.uiState.value.releases.releases.map { it.id })
        vm.selectRelease(ready.releases.releases.single().copy(vnId = "v1", id = "r1"))
        assertNull(vm.uiState.value.form.releaseId)
    }

    @Test fun `快速切换搜索取消旧请求且清空不再显示旧结果`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val vm = addVm(object : FakeVndb() {
            override suspend fun searchVn(query: String, page: Int, results: Int): VndbVnResponse {
                if (query == "A") { entered.complete(Unit); withContext(NonCancellable) { gate.await() } }
                return VndbVnResponse(listOf(VndbVnDto(if (query == "A") "v1" else "v2")))
            }
        })
        vm.onQueryChange("A"); advanceTimeBy(351); entered.await()
        vm.onQueryChange("B"); advanceTimeBy(351)
        vm.uiState.first { it.search.results.any { v -> v.id == "v2" } }
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("v2"), vm.uiState.value.search.results.map { it.id })
        vm.onQueryChange("")
        vm.uiState.first { it.search.query.isEmpty() && it.search.results.isEmpty() }
    }

    @Test fun `订单费用保存错误可见保留表单并能恢复重试`() = runTest {
        val id = purchases.createOrder(PurchaseOrder(0, "Batch", null, null, "JPY", null, 0, 0))
        val vm = OrderDetailViewModel(purchases, id)
        store.put("order", vm)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        vm.uiState.first { it.detail != null }
        vm.openNewExpense(); vm.onExpenseNameChange("Ship"); vm.onExpenseAmountChange("100")
        // A global ledger overflow is checked inside the write transaction, after form validation.
        val copy = OwnedCopy(0,"v1",null,"A","Manual",null,Long.MAX_VALUE,"JPY",CopyCondition.USED,null,null,null,null,null,0,0)
        val copyId = collection.addCopies(listOf(copy)).single()
        vm.saveExpense()
        assertTrue(vm.actionError.first { it != null }!!.contains("范围"))
        assertTrue(vm.uiState.first { !it.savingExpense }.editor.open)
        assertTrue(purchases.observeOrderDetail(id).first()!!.expenses.isEmpty())
        collection.delete(copyId)
        vm.saveExpense()
        vm.uiState.first { !it.editor.open && !it.savingExpense }
        assertEquals(100L, purchases.observeOrderDetail(id).first()!!.expenses.single().amountMinor)
        assertNull(vm.actionError.value)
    }

    @Test fun `收藏编辑失败不崩溃不修改记录并能修正后保存`() = runTest {
        val copy = OwnedCopy(0,"v1",null,"A","Manual",null,10,"JPY",CopyCondition.USED,null,null,null,null,null,0,0)
        val ids = collection.addCopies(listOf(copy, copy))
        val vm = CopyEditViewModel(collection, VnRepository(FakeVndb(), db.vnCacheDao(), Dispatchers.Unconfined), purchases, ids[0])
        store.put("edit", vm)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        vm.uiState.first { !it.loading && it.copy != null }
        vm.onPriceChange(Long.MAX_VALUE.toString())
        var saved = false
        vm.save { saved = true }
        assertNotNull(vm.actionError.first { it != null })
        assertFalse(saved)
        assertEquals(10L, collection.getById(ids[0])!!.priceMinor)
        assertEquals(Long.MAX_VALUE.toString(), vm.uiState.first { !it.saving }.form.priceText)
        vm.onPriceChange("20")
        val success = CompletableDeferred<Unit>()
        vm.save { saved = true; success.complete(Unit) }
        success.await()
        assertTrue(saved)
        assertEquals(20L, collection.getById(ids[0])!!.priceMinor)
    }

    @Test fun `新购入保存日语标题且表单更新不能改掉所选版本`() = runTest {
        val vm = addVm(object : FakeVndb() {
            override suspend fun getVn(vnId: String) = VndbVnDto(vnId, title = "Romanized", alttitle = "日本語の題名")
            override suspend fun getReleases(vnId: String, page: Int, results: Int) = VndbReleaseResponse(listOf(
                VndbReleaseDto("r1", title = "Limited", alttitle = "初回限定版", official = true),
                VndbReleaseDto("r2", title = "Fan Edition", official = false),
            ))
        })
        vm.selectVn(VndbVnDto("v1", title = "Romanized", alttitle = "日本語の題名").toDomain())
        val ready = vm.uiState.first { !it.releases.loading && it.releases.releases.isNotEmpty() }
        assertEquals(listOf("r1"), ready.releases.releases.map { it.id })
        vm.selectRelease(ready.releases.releases.single().copy(official = false))
        assertNull(vm.uiState.value.form.releaseId)
        vm.selectRelease(ready.releases.releases.single())
        val selected = vm.uiState.first { it.form.releaseId == "r1" }.form
        vm.onPurchaseFormChange(selected.copy(releaseId = "r2", releaseTitle = "Wrong", priceText = "6800", currency = "jpy", quantity = 2))
        vm.uiState.first { it.form.quantity == 2 }
        assertEquals("r1", vm.uiState.value.form.releaseId)
        vm.save()
        val event = vm.events.first() as AddFlowEvent.Saved
        assertEquals(2, event.copyIds.size)
        event.copyIds.forEach { id ->
            val copy = collection.getById(id)!!
            assertEquals("日本語の題名", copy.vnTitle)
            assertEquals("初回限定版", copy.releaseTitle)
            assertEquals(6800L, copy.priceMinor)
            assertEquals("JPY", copy.currency)
        }
    }
}
