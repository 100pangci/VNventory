package com.vnventory.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.data.backup.BackupFileStore
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.repository.BackupRepository
import com.vnventory.app.data.repository.SettingsRepository
import com.vnventory.app.domain.text.MessageFailure
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import com.vnventory.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShopChannelsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val viewModels = ViewModelStore()

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { viewModels.clear(); Dispatchers.resetMain() }

    private fun TestScope.store(): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = backgroundScope) {
        File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")
    }

    @Test fun `候选列表去掉首尾空白并保持顺序且拒绝空名和重复`() = runTest {
        val settings = SettingsRepository(store())
        assertTrue(settings.shopChannels.first().isEmpty())
        settings.addShopChannel("  駿河屋  ")
        settings.addShopChannel("Sofmap")
        assertEquals(listOf("駿河屋", "Sofmap"), settings.shopChannels.first())
        for (name in listOf(" ", "駿河屋", " sofmap ")) {
            try { settings.addShopChannel(name); fail("Expected invalid or duplicate name") }
            catch (_: IllegalArgumentException) { }
        }
        settings.renameShopChannel("駿河屋", " メルカリ ")
        assertEquals(listOf("メルカリ", "Sofmap"), settings.shopChannels.first())
        try { settings.renameShopChannel("メルカリ", "SOFMAP"); fail("Expected duplicate") }
        catch (e: IllegalArgumentException) { assertEquals(message(MessageKey.SHOP_DUPLICATE), (e as MessageFailure).userMessage) }
        assertEquals(listOf("メルカリ", "Sofmap"), settings.shopChannels.first())
        settings.removeShopChannel("メルカリ")
        settings.removeShopChannel("already removed")
        assertEquals(listOf("Sofmap"), settings.shopChannels.first())
    }

    @Test fun `并发写入不会丢失候选项且同名只能保存一次`() = runTest {
        val settings = SettingsRepository(store())
        coroutineScope { (1..8).map { async { settings.addShopChannel("Shop $it") } }.awaitAll() }
        assertEquals((1..8).map { "Shop $it" }.toSet(), settings.shopChannels.first().toSet())
        val successes = coroutineScope {
            List(2) { async { try { settings.addShopChannel("Same"); true } catch (_: IllegalArgumentException) { false } } }.awaitAll()
        }
        assertEquals(1, successes.count { it })
        assertEquals(1, settings.shopChannels.first().count { it == "Same" })
    }

    @Test fun `录入和恢复的Unicode名称使用相同的去重规则`() = runTest {
        val settings = SettingsRepository(store())
        val names = listOf("İ", "i")
        settings.restorePreferences(null, names)
        assertEquals(names, settings.shopChannels.first())
        settings.restorePreferences(null, emptyList())
        names.forEach { settings.addShopChannel(it) }
        assertEquals(names, settings.shopChannels.first())
    }

    @Test fun `候选项写入DataStore后重建仓库仍然存在`() = runTest {
        val file = File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")
        val firstJob = SupervisorJob()
        val first = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + UnconfinedTestDispatcher(testScheduler))) { file })
        first.addShopChannel("駿河屋")
        firstJob.cancelAndJoin()
        val second = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
        assertEquals(listOf("駿河屋"), second.shopChannels.first())
    }

    @Test fun `损坏列表不能静默覆盖成空列表或导出默认值`() = runTest {
        val dataStore = store()
        val key = stringPreferencesKey("shop_channels")
        dataStore.edit { it[key] = "{broken}" }
        val settings = SettingsRepository(dataStore)
        try { settings.snapshot(); fail("Expected invalid preferences") }
        catch (e: IllegalArgumentException) { assertEquals(message(MessageKey.SHOP_DATA_INVALID), (e as MessageFailure).userMessage) }
        try { settings.addShopChannel("New"); fail("Expected invalid preferences") }
        catch (_: IllegalArgumentException) { }
        assertEquals("{broken}", dataStore.data.first()[key])
    }

    @Test fun `编辑失败保持输入错误可见且修正后可保存`() = runTest {
        val settings = SettingsRepository(store())
        settings.addShopChannel("駿河屋")
        val db = Room.inMemoryDatabaseBuilder(context, VNventoryDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = BackupRepository(db, settings, Dispatchers.Unconfined)
            val vm = SettingsViewModel(settings, repository, BackupFileStore(context.contentResolver, repository, Dispatchers.Unconfined))
            viewModels.put("shops", vm)
            vm.openShopEditor()
            vm.onShopNameChange(" 駿河屋 ")
            vm.saveShop()
            assertEquals(message(MessageKey.SHOP_DUPLICATE), vm.actionError.first { it != null })
            assertTrue(vm.shopEditor.value.open)
            assertEquals(" 駿河屋 ", vm.shopEditor.value.name)
            assertFalse(vm.shopsBusy.value)
            vm.onShopNameChange("メルカリ")
            vm.saveShop()
            settings.shopChannels.first { it.size == 2 }
            vm.shopEditor.first { !it.open }
            assertNull(vm.actionError.value)
        } finally { db.close() }
    }
}
