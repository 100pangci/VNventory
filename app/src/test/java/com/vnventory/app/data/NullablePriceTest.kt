package com.vnventory.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.repository.*
import com.vnventory.app.domain.model.*
import com.vnventory.app.ui.add.AddFlowEvent
import com.vnventory.app.ui.add.AddFlowViewModel
import com.vnventory.app.ui.edit.CopyEditViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NullablePriceTest {
    @Test fun `空价格添加多盒可独立编辑且零价统计排序准确`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, VNventoryDatabase::class.java).allowMainThreadQueries().build()
        val store = ViewModelStore()
        try {
            val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) {
                File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")
            })
            assertFalse(settings.showShelfPrices.first())
            assertFalse(settings.showPriceStats.first())
            settings.setShowShelfPrices(true)
            assertTrue(settings.showShelfPrices.first())
            assertFalse(settings.showPriceStats.first())
            settings.setShowPriceStats(true)
            settings.setShowShelfPrices(false)
            assertFalse(settings.showShelfPrices.first())
            assertTrue(settings.showPriceStats.first())

            val collection = CollectionRepository(db, db.ownedCopyDao(), db.vnCacheDao(), db.expenseDao(), Dispatchers.Unconfined)
            val purchases = PurchaseRepository(db, db.purchaseOrderDao(), db.expenseDao(), db.ownedCopyDao(), Dispatchers.Unconfined)
            val vn = VnRepository(FakeVndb(), db.vnCacheDao(), Dispatchers.Unconfined)
            val add = AddFlowViewModel(vn, collection, purchases, settings, null).also { store.put("add", it) }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { add.uiState.collect() }
            add.selectVn(com.vnventory.app.domain.model.VnInfo("v1", "A", null, null, null, null))
            add.selectManualVersion()
            add.onManualTitleChange("Manual")
            add.onPurchaseFormChange(add.uiState.first { it.form.releaseTitle == "Manual" }.form.copy(quantity = 2))
            add.save()
            val ids = (add.events.first() as AddFlowEvent.Saved).copyIds
            assertEquals(2, ids.size)
            assertNotEquals(ids[0], ids[1])
            ids.forEach { assertNull(collection.getById(it)!!.priceMinor) }
            assertTrue(collection.observePriceTotals().first().isEmpty())
            assertEquals(0, collection.observePricedCopyCount().first())
            val orderId = purchases.createOrder(PurchaseOrder(0, "Batch", null, null, "CNY", null, 0, 0))
            purchases.assignCopiesToOrder(ids, orderId)
            val unknownOrder = purchases.observeOrders().first().single()
            assertEquals(2, unknownOrder.copyCount)
            assertEquals(0, unknownOrder.pricedCopyCount)
            assertTrue(unknownOrder.goodsTotals.isEmpty())
            assertTrue(unknownOrder.grandTotals.isEmpty())

            val edit = CopyEditViewModel(collection, vn, purchases, ids[0], settings).also { store.put("edit", it) }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { edit.uiState.collect() }
            assertEquals("", edit.uiState.first { !it.loading }.form.priceText)
            edit.onPriceChange("0")
            val saved = CompletableDeferred<Unit>()
            edit.save { saved.complete(Unit) }
            saved.await()
            assertEquals(0L, collection.getById(ids[0])!!.priceMinor)
            assertNull(collection.getById(ids[1])!!.priceMinor)
            assertEquals(1, collection.observePricedCopyCount().first())
            assertEquals(2, collection.observeCopyCount().first())
            assertEquals(0L, collection.observePriceTotals().first().single().total)
            assertEquals(1, purchases.observeOrders().first().single().pricedCopyCount)
            val known = collection.getById(ids[0])!!
            collection.addCopies(listOf(known.copy(id = 0, priceMinor = 123)))
            listOf(CollectionSort.PRICE_ASC, CollectionSort.PRICE_DESC).forEach { sort ->
                val copies = collection.observeCollection(CollectionQuery(sort = sort)).first()
                assertNull(copies.last().priceMinor)
                assertEquals(3, copies.size)
            }
            assertEquals(2, collection.observePricedCopyCount().first())
            assertEquals(123L, collection.observePriceTotals().first().single().total)
            edit.onPriceChange("")
            val cleared = CompletableDeferred<Unit>()
            edit.save { cleared.complete(Unit) }
            cleared.await()
            assertNull(collection.getById(ids[0])!!.priceMinor)
            assertEquals(1, collection.observePricedCopyCount().first())
        } finally {
            store.clear()
            db.close()
            Dispatchers.resetMain()
        }
    }
}
