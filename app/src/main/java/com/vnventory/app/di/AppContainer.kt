package com.vnventory.app.di

import android.content.Context
import com.vnventory.app.BuildConfig
import com.vnventory.app.data.backup.BackupFileStore
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.remote.vndb.VndbApi
import com.vnventory.app.data.remote.vndb.VndbClientFactory
import com.vnventory.app.data.repository.BackupRepository
import com.vnventory.app.data.repository.CollectionRepository
import com.vnventory.app.data.repository.PurchaseRepository
import com.vnventory.app.data.repository.SettingsRepository
import com.vnventory.app.data.repository.VnRepository
import com.vnventory.app.data.repository.settingsDataStore

/**
 * 手动依赖容器（MVP 规模足够，避免引入 Hilt 带来的构建复杂度）。
 * 数据库、HTTP 客户端均为懒加载单例。
 */
class AppContainer(private val appContext: Context) {

    val database: VNventoryDatabase by lazy { VNventoryDatabase.build(appContext) }

    private val httpClient by lazy {
        VndbClientFactory.create(versionName = BuildConfig.VERSION_NAME, debugLogging = BuildConfig.DEBUG)
    }

    val vndbApi: VndbApi by lazy { VndbApi(httpClient) }

    val vnRepository: VnRepository by lazy {
        VnRepository(api = vndbApi, vnCacheDao = database.vnCacheDao())
    }

    val collectionRepository: CollectionRepository by lazy {
        CollectionRepository(
            database = database,
            ownedCopyDao = database.ownedCopyDao(),
            vnCacheDao = database.vnCacheDao(),
            expenseDao = database.expenseDao(),
        )
    }

    val purchaseRepository: PurchaseRepository by lazy {
        PurchaseRepository(
            database = database,
            orderDao = database.purchaseOrderDao(),
            expenseDao = database.expenseDao(),
            ownedCopyDao = database.ownedCopyDao(),
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext.settingsDataStore)
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(database, settingsRepository)
    }

    val backupFileStore: BackupFileStore by lazy {
        BackupFileStore(appContext.contentResolver, backupRepository)
    }
}
