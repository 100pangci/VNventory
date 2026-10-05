package com.vnventory.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.core.AppResult
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.mapper.toDomain
import com.vnventory.app.data.remote.vndb.*
import com.vnventory.app.data.repository.*
import com.vnventory.app.domain.model.*
import com.vnventory.app.ui.add.AddFlowViewModel
import com.vnventory.app.ui.add.AddFlowEvent
import com.vnventory.app.ui.theme.ThemeViewModel
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
class TitlePersistenceTest {
    @Test fun `cache snapshots offline display search sort and preference never refetch or rewrite`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, VNventoryDatabase::class.java).allowMainThreadQueries().build()
        val models = ViewModelStore()
        try {
            val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) {
                File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")
            })
            var requests = 0
            var offline = false
            val vnDto = VndbVnDto("v1", "Sakura no Uta", "サクラノ詩")
            val releaseDto = VndbReleaseDto("r1", "First Edition", "初回版")
            val api = object : FakeVndb() {
                override suspend fun getVn(vnId: String): VndbVnDto { requests++; if (offline) error("offline"); return vnDto }
                override suspend fun searchVn(query: String, page: Int, results: Int): VndbVnResponse { requests++; if (offline) error("offline"); return VndbVnResponse(listOf(vnDto)) }
                override suspend fun getReleases(vnId: String, page: Int, results: Int): VndbReleaseResponse { requests++; if (offline) error("offline"); return VndbReleaseResponse(listOf(releaseDto)) }
            }
            val vnRepo = VnRepository(api, db.vnCacheDao(), Dispatchers.Unconfined)
            val collection = CollectionRepository(db, db.ownedCopyDao(), db.vnCacheDao(), db.expenseDao(), Dispatchers.Unconfined)
            val purchase = PurchaseRepository(db, db.purchaseOrderDao(), db.expenseDao(), db.ownedCopyDao(), Dispatchers.Unconfined)
            vnRepo.fetchVn("v1")
            vnRepo.fetchReleases("v1")
            val cachedVn = vnRepo.getCachedVn("v1")!!
            val cachedRelease = vnRepo.getCachedRelease("r1", "v1")!!
            assertEquals(vnDto.toDomain().copy(fromCache = true), cachedVn)
            assertEquals(releaseDto.toDomain("v1"), cachedRelease)
            offline = true
            for (keyword in listOf("サクラノ詩", "Sakura no Uta")) {
                val result = vnRepo.searchVn(keyword) as AppResult.Success
                assertTrue(result.data.offline)
                assertEquals("Sakura no Uta", result.data.items.single().romanizedTitle)
            }
            offline = false
            settings.setTitleDisplayMode(TitleDisplayMode.ROMANIZED)
            val add = AddFlowViewModel(vnRepo, collection, purchase, settings, null).also { models.put("add", it) }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { add.uiState.collect() }
            add.selectVn(cachedVn)
            val ready = add.uiState.first { !it.releases.loading && it.releases.releases.isNotEmpty() }
            add.selectRelease(ready.releases.releases.single())
            add.save()
            val id = (add.events.first() as AddFlowEvent.Saved).copyIds.single()
            val snapshot = collection.getById(id)!!
            assertEquals("サクラノ詩", snapshot.vnOriginalTitle)
            assertEquals("Sakura no Uta", snapshot.vnRomanizedTitle)
            assertEquals("初回版", snapshot.releaseOriginalTitle)
            assertEquals("First Edition", snapshot.releaseRomanizedTitle)
            db.vnCacheDao().clearVnCache()
            db.vnCacheDao().clearReleaseCache()
            offline = true
            val before = requests
            val display = ThemeViewModel(settings).also { models.put("display", it) }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { display.titleDisplayMode.collect() }
            for (mode in TitleDisplayMode.entries) {
                settings.setTitleDisplayMode(mode)
                display.titleDisplayMode.first { it == mode }
                assertEquals(snapshot, collection.getById(id))
                assertEquals(if (mode == TitleDisplayMode.ORIGINAL) "サクラノ詩" else "Sakura no Uta", snapshot.displayTitle(mode))
                assertEquals(if (mode == TitleDisplayMode.ORIGINAL) "初回版" else "First Edition", (snapshot.displayReleaseName(mode) as com.vnventory.app.domain.text.Message.Literal).value)
                for (keyword in listOf("サクラノ詩", "Sakura no Uta", "初回版", "First Edition")) {
                    assertEquals(id, collection.observeCollection(CollectionQuery(search = keyword, titleDisplayMode = mode)).first().single().id)
                }
            }
            assertEquals(before, requests)
            val manual = snapshot.copy(id = 0, releaseId = null, releaseTitle = "My edition", releaseOriginalTitle = null, releaseRomanizedTitle = null)
            for (mode in TitleDisplayMode.entries) assertEquals("My edition", (manual.displayReleaseName(mode) as com.vnventory.app.domain.text.Message.Literal).value)
            val second = manual.copy(vnId = "v2", vnTitle = "Legacy", vnOriginalTitle = "あ", vnRomanizedTitle = "Zeta")
            val secondId = collection.addCopies(listOf(second)).single()
            assertEquals(listOf(secondId, id), collection.observeCollection(CollectionQuery(sort = CollectionSort.TITLE_ASC)).first().map { it.id })
            assertEquals(listOf(id, secondId), collection.observeCollection(CollectionQuery(sort = CollectionSort.TITLE_ASC, titleDisplayMode = TitleDisplayMode.ROMANIZED)).first().map { it.id })
        } finally {
            models.clear()
            db.close()
            Dispatchers.resetMain()
        }
    }
}
