package com.vnventory.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.data.repository.SettingsRepository
import com.vnventory.app.data.repository.BackupRepository
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.backup.BackupFileStore
import com.vnventory.app.domain.model.AppearancePreferences
import com.vnventory.app.domain.model.ThemeMode
import com.vnventory.app.ui.theme.ThemeViewModel
import com.vnventory.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
class AppearancePreferencesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun file() = File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")

    @Test fun `默认跟随系统且保持固定品牌配色`() = runTest {
        val preferencesFile = file()
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { preferencesFile })
        assertEquals(AppearancePreferences(), settings.appearance.first())
        assertEquals(AppearancePreferences(), settings.snapshot().appearance)
        for (systemDark in listOf(false, true)) {
            assertEquals(systemDark, ThemeMode.SYSTEM.isDark(systemDark))
            assertFalse(ThemeMode.LIGHT.isDark(systemDark))
            assertTrue(ThemeMode.DARK.isDark(systemDark))
        }
    }

    @Test fun `主题动态取色和价格展示互相独立且重建后仍持久化`() = runTest {
        val preferencesFile = file()
        val job = SupervisorJob()
        val first = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(job + UnconfinedTestDispatcher(testScheduler))) { preferencesFile })
        first.setThemeMode(ThemeMode.DARK)
        first.setDynamicColor(true)
        first.setShowShelfPrices(true)
        first.setShowShelfReleaseNames(true)
        first.setThemeMode(ThemeMode.LIGHT)
        assertEquals(AppearancePreferences(ThemeMode.LIGHT, true), first.appearance.first())
        assertTrue(first.showShelfPrices.first())
        assertFalse(first.showPriceStats.first())
        assertTrue(first.showShelfReleaseNames.first())
        first.setDynamicColor(false)
        assertEquals(AppearancePreferences(ThemeMode.LIGHT, false), first.appearance.first())
        job.cancelAndJoin()
        val reopened = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { preferencesFile })
        assertEquals(AppearancePreferences(ThemeMode.LIGHT, false), reopened.appearance.first())
        assertTrue(reopened.showShelfPrices.first())
        assertTrue(reopened.showShelfReleaseNames.first())
    }

    @Test fun `应用主题状态实时订阅设置且首次读取前不猜主题`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val preferencesFile = file()
            val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { preferencesFile })
            settings.setThemeMode(ThemeMode.DARK)
            val vm = ThemeViewModel(settings).also { store.put("theme", it) }
            assertNull(vm.appearance.value)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.appearance.collect() }
            assertEquals(ThemeMode.DARK, vm.appearance.first { it != null }!!.themeMode)
            settings.setDynamicColor(true)
            assertEquals(AppearancePreferences(ThemeMode.DARK, true), vm.appearance.first { it?.dynamicColor == true })
            settings.setThemeMode(ThemeMode.SYSTEM)
            assertEquals(AppearancePreferences(ThemeMode.SYSTEM, true), vm.appearance.first { it?.themeMode == ThemeMode.SYSTEM })
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun `未知本地主题值安全跟随系统而旧备份不覆盖本机外观`() = runTest {
        val preferencesFile = file()
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { preferencesFile }
        val settings = SettingsRepository(dataStore)
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "FUTURE" }
        assertEquals(ThemeMode.SYSTEM, settings.appearance.first().themeMode)
        settings.setThemeMode(ThemeMode.DARK)
        settings.setDynamicColor(true)
        settings.restorePreferences("JPY", null, false, false)
        assertEquals(AppearancePreferences(ThemeMode.DARK, true), settings.appearance.first())
        settings.restorePreferences(null, null, appearance = AppearancePreferences(ThemeMode.LIGHT, false))
        assertEquals(AppearancePreferences(ThemeMode.LIGHT, false), settings.snapshot().appearance)
    }

    @Test fun `偏好页ViewModel写入驱动应用主题而不会重置价格设置`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val models = ViewModelStore()
        val db = Room.inMemoryDatabaseBuilder(context, VNventoryDatabase::class.java).allowMainThreadQueries().build()
        try {
            val preferencesFile = file()
            val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { preferencesFile })
            val backup = BackupRepository(db, settings, Dispatchers.Unconfined)
            val editor = SettingsViewModel(settings, backup, BackupFileStore(context.contentResolver, backup, Dispatchers.Unconfined)).also { models.put("settings", it) }
            val appTheme = ThemeViewModel(settings).also { models.put("theme", it) }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { appTheme.appearance.collect() }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { editor.appearance.collect() }
            appTheme.appearance.first { it != null }
            editor.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, appTheme.appearance.first { it?.themeMode == ThemeMode.DARK }!!.themeMode)
            editor.setDynamicColor(true)
            assertEquals(AppearancePreferences(ThemeMode.DARK, true), appTheme.appearance.first { it?.dynamicColor == true })
            assertEquals(AppearancePreferences(ThemeMode.DARK, true), editor.appearance.first { it.dynamicColor })
            assertFalse(settings.showShelfPrices.first())
            assertFalse(settings.showPriceStats.first())
        } finally {
            models.clear()
            db.close()
            Dispatchers.resetMain()
        }
    }
}
