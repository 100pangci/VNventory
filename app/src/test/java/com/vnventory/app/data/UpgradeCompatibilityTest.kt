package com.vnventory.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.data.backup.BackupCodec
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.remote.fx.FxFetchResult
import com.vnventory.app.data.remote.fx.FxRateClient
import com.vnventory.app.data.remote.fx.FxRateSource
import com.vnventory.app.data.repository.BackupRepository
import com.vnventory.app.data.repository.BudgetRepository
import com.vnventory.app.data.repository.CollectionRepository
import com.vnventory.app.data.repository.FxFreezer
import com.vnventory.app.data.repository.PurchaseRepository
import com.vnventory.app.data.repository.SettingsRepository
import com.vnventory.app.domain.cost.BudgetIssueReason
import com.vnventory.app.domain.model.BudgetPreferences
import com.vnventory.app.domain.model.CollectionQuery
import com.vnventory.app.domain.model.TitleDisplayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * 升级兼容性回归：直接用上一版（v1.0.4）真实导出的 Room v4 schema 建库并写入数据，
 * 再用当前代码打开，验证迁移、读取、成本计算、预算汇总、自动定格和备份往返都不炸。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpgradeCompatibilityTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.of(2026, 10, 13)

    private class FakeSource(private val rate: Long) : FxRateSource {
        val calls = mutableListOf<Triple<String, String, LocalDate>>()
        override val name = "fake"
        override suspend fun fetch(from: String, to: String, date: LocalDate): FxFetchResult {
            calls += Triple(from, to, date)
            return FxFetchResult.Success(rate, date, name)
        }
    }

    private fun TestScope.settings(): SettingsRepository = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb") },
    )

    /** 按 v1.0.4 导出的真实 schema 建一个 v4 数据库并写入典型数据。 */
    private fun buildLegacyDatabase(name: String) {
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = javaClass.classLoader!!.getResourceAsStream("com.vnventory.app.data.local.VNventoryDatabase/4.json")!!
            .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject.getValue("database").jsonObject }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            schema.getValue("entities").jsonArray.forEach { value ->
                val entity = value.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                old.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    old.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { old.execSQL(it.jsonPrimitive.content) }
            old.execSQL("INSERT INTO vn_cache(vndbId,title,altTitle,released,imageUrl,description,fetchedAt) VALUES ('v17','作品名',NULL,NULL,NULL,NULL,0)")
            old.execSQL("INSERT INTO release_cache(vndbId,title,released,platforms,languages,publishers,jan,minAge,official,packagingImageUrl,fetchedAt) VALUES ('r123','初回限定版',NULL,'win','ja','Pub',NULL,NULL,NULL,NULL,0)")
            old.execSQL("INSERT INTO release_vn VALUES ('v17','r123')")
            old.execSQL("INSERT INTO purchase_order VALUES (1,'骏河屋一批',NULL,'2026-09-30','JPY',NULL,0,0)")
            // 1：旧订单里的日元盒（窗口外）；2：同币种新盒（直接计入）；3：外币新盒（窗口内，可自动定格）。
            old.execSQL(
                """INSERT INTO owned_copy(id,vnId,releaseId,vnTitle,releaseTitle,coverUrl,priceMinor,currency,condition,conditionNote,purchaseDate,shop,orderId,notes,createdAt,updatedAt)
                   VALUES (1,'v17','r123','作品名','初回限定版',NULL,5000,'JPY','USED',NULL,'2026-09-30','駿河屋',1,NULL,0,0)"""
            )
            old.execSQL(
                """INSERT INTO owned_copy(id,vnId,releaseId,vnTitle,releaseTitle,coverUrl,priceMinor,currency,condition,conditionNote,purchaseDate,shop,orderId,notes,createdAt,updatedAt)
                   VALUES (2,'v17',NULL,'作品名','手动版本',NULL,6800,'CNY','USED',NULL,'2026-10-12',NULL,NULL,NULL,0,0)"""
            )
            old.execSQL(
                """INSERT INTO owned_copy(id,vnId,releaseId,vnTitle,releaseTitle,coverUrl,priceMinor,currency,condition,conditionNote,purchaseDate,shop,orderId,notes,createdAt,updatedAt)
                   VALUES (3,'v17','r123','作品名','初回限定版',NULL,1200,'JPY','USED',NULL,'2026-10-12',NULL,NULL,NULL,0,0)"""
            )
            old.execSQL("INSERT INTO expense(id,orderId,name,category,amountMinor,currency,mode,notes,createdAt) VALUES (1,1,'国际运费','INTERNATIONAL_SHIPPING',100,'JPY','MANUAL',NULL,0)")
            old.execSQL("INSERT INTO expense_allocation VALUES (1,1,100)")
            old.version = 4
        }
    }

    @Test fun `v1_0_4数据库升级后事实可读预算汇总与自动定格安全`() = runTest {
        val name = "upgrade-compat.db"
        buildLegacyDatabase(name)
        val db = Room.databaseBuilder(context, VNventoryDatabase::class.java, name)
            .addMigrations(
                VNventoryDatabase.MIGRATION_1_2, VNventoryDatabase.MIGRATION_2_3,
                VNventoryDatabase.MIGRATION_3_4, VNventoryDatabase.MIGRATION_4_5,
            )
            .allowMainThreadQueries().build()
        try {
            val collection = CollectionRepository(db, db.ownedCopyDao(), db.vnCacheDao(), db.expenseDao(), Dispatchers.Unconfined)
            val purchases = PurchaseRepository(db, db.purchaseOrderDao(), db.expenseDao(), db.ownedCopyDao(), Dispatchers.Unconfined)

            // 旧购买事实全部可读，迁移只加空列。
            val copies = collection.observeCollection(CollectionQuery()).first()
            assertEquals(3, copies.size)
            assertTrue(copies.all { it.fxRateScaled == null && it.fxToCurrency == null })
            assertEquals("初回限定版", copies.first { it.id == 1L }.releaseTitle)
            val detail = purchases.observeOrderDetail(1).first()!!
            assertEquals(1, detail.copies.size)
            assertTrue(detail.breakdown.issues.isEmpty())
            assertEquals(mapOf("JPY" to 5100L), detail.breakdown.totalsByCurrency)
            assertEquals(listOf(100L), detail.expenses.single().allocations.values.toList())

            // 预算汇总：默认未开启、目标币种取默认货币；同币种直接计入，外币未定格列为未计入。
            val settings = settings()
            val budget = BudgetRepository(collection, purchases, settings, Dispatchers.Unconfined, today = { today })
            val overview = budget.observeOverview().first()
            assertEquals(false, overview.preferences.enabled)
            assertEquals("CNY", overview.targetCurrency)
            assertEquals(6800L, overview.summary.totalForYear(2026))
            assertEquals(
                listOf(BudgetIssueReason.NOT_FROZEN, BudgetIssueReason.NOT_FROZEN, BudgetIssueReason.NOT_FROZEN),
                overview.summary.issues.map { it.reason },
            )

            // 自动重试只处理最近窗口内外币记录：旧订单（9-30）与费用不发起请求。
            val source = FakeSource(rate = 12_500_000L)
            val freezer = FxFreezer(
                database = db, settingsRepository = settings,
                client = FxRateClient(listOf(source), today = { today }),
                scope = backgroundScope, io = StandardTestDispatcher(testScheduler), today = { today },
            )
            freezer.requestRecentRetry().join()
            assertEquals(listOf(Triple("JPY", "CNY", LocalDate.of(2026, 10, 12))), source.calls)
            assertNotNull(db.ownedCopyDao().getById(3)!!.fxRateScaled)
            assertNull(db.ownedCopyDao().getById(1)!!.fxRateScaled)
            assertNull(db.expenseDao().getById(1)!!.fxRateScaled)

            // 显式补全旧记录。
            val result = freezer.backfillAll()
            assertEquals(2, result.succeeded)
            assertEquals(2, source.calls.size)

            // 升级后的数据导出备份可完整往返并恢复。
            val backups = BackupRepository(db, settings, Dispatchers.Unconfined)
            val data = backups.snapshot()
            val decoded = BackupCodec.decode(ByteArrayInputStream(BackupCodec.encode(data)))
            assertEquals(3, decoded.copies.size)
            backups.restore(decoded, replace = true, restoreCurrency = false)
            assertEquals(3, db.ownedCopyDao().getAll().size)
            assertNotNull(db.ownedCopyDao().getAll().first { it.fxRateScaled != null })
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun `旧版DataStore升级后预算默认关闭且旧设置保持`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")
        }
        // 只写上一版存在的键，模拟真实升级现场。
        store.edit { prefs ->
            prefs[stringPreferencesKey("default_currency")] = "JPY"
            prefs[stringPreferencesKey("shop_channels")] = Json.encodeToString(listOf("駿河屋"))
            prefs[booleanPreferencesKey("show_shelf_prices")] = true
            prefs[stringPreferencesKey("title_display_mode")] = "ROMANIZED"
        }
        val settings = SettingsRepository(store)
        assertEquals("JPY", settings.defaultCurrency.first())
        assertEquals("JPY", settings.fxTargetCurrency.first())
        assertTrue(settings.showShelfPrices.first())
        assertEquals(TitleDisplayMode.ROMANIZED, settings.titleDisplayMode.first())
        assertEquals(listOf("駿河屋"), settings.shopChannels.first())
        assertEquals(BudgetPreferences(), settings.budgetPreferences.first())

        // 新版写入预算设置后，旧键不受影响。
        settings.setBudgetEnabled(true)
        settings.setBudgetCurrency("CNY")
        settings.setBudgetYearlyAmount(2026, 600_000L)
        assertEquals(
            BudgetPreferences(enabled = true, currency = "CNY", yearlyAmounts = mapOf(2026 to 600_000L), includeFees = true),
            settings.budgetPreferences.first(),
        )
        assertEquals("JPY", settings.defaultCurrency.first())
        assertEquals(listOf("駿河屋"), settings.shopChannels.first())
    }
}
