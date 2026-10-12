package com.vnventory.app.di

import android.content.Context
import com.vnventory.app.BuildConfig
import com.vnventory.app.data.backup.BackupFileStore
import com.vnventory.app.data.image.CoilCoverWarmer
import com.vnventory.app.data.image.CoverWarmer
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.remote.fx.FxClientFactory
import com.vnventory.app.data.remote.fx.FxRateClient
import com.vnventory.app.data.remote.vndb.VndbApi
import com.vnventory.app.data.remote.vndb.VndbClientFactory
import com.vnventory.app.data.repository.BackupRepository
import com.vnventory.app.data.repository.BudgetRepository
import com.vnventory.app.data.repository.CollectionRepository
import com.vnventory.app.data.repository.FxFreezer
import com.vnventory.app.data.repository.PurchaseRepository
import com.vnventory.app.data.repository.SettingsRepository
import com.vnventory.app.data.repository.VnRepository
import com.vnventory.app.data.repository.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手动依赖容器（MVP 规模足够，避免引入 Hilt 带来的构建复杂度）。
 * 数据库、HTTP 客户端均为懒加载单例。
 */
class AppContainer(private val appContext: Context) {

    /** 应用级作用域：保存后的后台定格不随页面离开而取消。 */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: VNventoryDatabase by lazy { VNventoryDatabase.build(appContext) }

    private val httpClient by lazy {
        VndbClientFactory.create(versionName = BuildConfig.VERSION_NAME, debugLogging = BuildConfig.DEBUG)
    }

    private val fxHttpClient by lazy {
        FxClientFactory.create(versionName = BuildConfig.VERSION_NAME, debugLogging = BuildConfig.DEBUG)
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

    val budgetRepository: BudgetRepository by lazy {
        BudgetRepository(collectionRepository, purchaseRepository, settingsRepository)
    }

    /** 汇率定格服务：保存后后台执行；失败不影响保存，可随时重试或手动填写。 */
    val fxFreezer: FxFreezer by lazy {
        FxFreezer(
            database = database,
            settingsRepository = settingsRepository,
            client = FxRateClient.default(fxHttpClient),
            scope = applicationScope,
        )
    }

    /** 保存收藏/绑定版本后预热封面缓存，保证之后断网或受限网络下书架仍有封面。 */
    val coverWarmer: CoverWarmer by lazy { CoilCoverWarmer(appContext) }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(database, settingsRepository)
    }

    val backupFileStore: BackupFileStore by lazy {
        BackupFileStore(appContext.contentResolver, backupRepository)
    }
}
