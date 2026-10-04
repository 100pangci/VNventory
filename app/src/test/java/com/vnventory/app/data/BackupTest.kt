package com.vnventory.app.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.data.backup.BackupCodec
import com.vnventory.app.data.backup.BackupData
import com.vnventory.app.data.backup.BackupFileStore
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.local.entity.ExpenseAllocationEntity
import com.vnventory.app.data.local.entity.ExpenseEntity
import com.vnventory.app.data.local.entity.OwnedCopyEntity
import com.vnventory.app.data.local.entity.PurchaseOrderEntity
import com.vnventory.app.data.local.entity.VnCacheEntity
import com.vnventory.app.data.repository.BackupRepository
import com.vnventory.app.data.repository.PurchaseRepository
import com.vnventory.app.data.repository.SettingsRepository
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.ExpenseCategory
import com.vnventory.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID
import com.vnventory.app.domain.text.MessageFailure
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import com.vnventory.app.ui.text.resolve
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    private lateinit var db: VNventoryDatabase
    private val vmStore = ViewModelStore()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(context, VNventoryDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() {
        vmStore.clear()
        db.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.settings(): SettingsRepository = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb") },
    )

    private fun repository(settings: SettingsRepository) = BackupRepository(db, settings, Dispatchers.Unconfined)

    private fun sample(): BackupData {
        val order = PurchaseOrderEntity(9, "骏河屋一批", "駿河屋", LocalDate.of(2026, 9, 30), "JPY", "中文与日文备注", 10, 20)
        val copy = OwnedCopyEntity(3, "v17", "r123", "作品名", "初回限定版", "https://example.test/cover.jpg",
            50, "JPY", CopyCondition.CUSTOM, "外盒缺角", LocalDate.of(2026, 10, 1), "店铺", 9, "收藏备注", 30, 40)
        return BackupData(
            exportedAt = 1_791_000_000_000,
            defaultCurrency = "JPY",
            orders = listOf(order, order.copy(id = 12, title = "空批次", currency = "CNY")),
            copies = listOf(copy, copy.copy(id = 7, priceMinor = 60, condition = CopyCondition.UNOPENED, conditionNote = null),
                copy.copy(id = 8, releaseId = null, releaseTitle = "手动版本", priceMinor = 12345, currency = "CNY", orderId = null, purchaseDate = null)),
            expenses = listOf(
                ExpenseEntity(2, 9, "手续费", ExpenseCategory.FEE, 2, "JPY", AllocationMode.EQUAL, null, 50),
                ExpenseEntity(4, 9, "运费", ExpenseCategory.SHIPPING, 100, "JPY", AllocationMode.EQUAL, "池化", 60),
                ExpenseEntity(5, 9, "税费", ExpenseCategory.TAX, 100, "JPY", AllocationMode.MANUAL, "部分分摊", 70),
                ExpenseEntity(6, 9, "比例", ExpenseCategory.OTHER, 90, "CNY", AllocationMode.BY_PRICE, null, 80),
            ),
            allocations = listOf(ExpenseAllocationEntity(5, 3, 30), ExpenseAllocationEntity(5, 7, 50)),
        )
    }

    private fun decode(bytes: ByteArray) = BackupCodec.decode(ByteArrayInputStream(bytes))

    private fun invalid(data: BackupData) {
        assertThrows(IllegalArgumentException::class.java) { decode(BackupCodec.encode(data)) }
    }

    @Test fun `空价格零价格与展示设置往返旧版恢复使用关闭默认`() = runTest {
        val original = sample().copy(copies = sample().copies.mapIndexed { index, copy ->
            copy.copy(priceMinor = if (index == 0) null else 0)
        }, showShelfPrices = true, showPriceStats = true)
        assertEquals(original, decode(BackupCodec.encode(original)))
        val settings = settings()
        val repo = repository(settings)
        repo.restore(decode(BackupCodec.encode(original)), true, true)
        val restored = repo.snapshot()
        assertNull(restored.copies.first().priceMinor)
        assertEquals(0L, restored.copies[1].priceMinor)
        assertTrue(settings.showShelfPrices.first())
        assertTrue(settings.showPriceStats.first())
        val root = Json.parseToJsonElement(BackupCodec.encode(sample()).decodeToString()).jsonObject
        val legacy = JsonObject(root + mapOf(
            "schemaVersion" to kotlinx.serialization.json.JsonPrimitive(1),
            "settings" to JsonObject(root.getValue("settings").jsonObject - "showShelfPrices" - "showPriceStats"),
        ))
        val old = decode(legacy.toString().encodeToByteArray())
        assertEquals(50L, old.copies.first().priceMinor)
        repo.restore(old, true, true)
        assertFalse(settings.showShelfPrices.first())
        assertFalse(settings.showPriceStats.first())
        assertEquals("手续费", repo.snapshot().expenses.first().name)
    }

    @Test fun `JSON往返保留配置快照日期金额与全部购买事实`() {
        val original = sample()
        val bytes = BackupCodec.encode(original)
        assertEquals(original, decode(bytes))
        val json = bytes.decodeToString()
        assertTrue(json.contains("VNventoryBackup"))
        assertTrue(json.contains("初回限定版"))
        assertTrue(json.contains("2026-10-01"))
        assertFalse(json.contains("vn_cache"))
        val veryLarge = original.copy(orders = emptyList(), copies = listOf(original.copies[0].copy(orderId = null, priceMinor = Long.MAX_VALUE)), expenses = emptyList(), allocations = emptyList())
        assertEquals(Long.MAX_VALUE, decode(BackupCodec.encode(veryLarge)).copies.single().priceMinor)
    }

    @Test fun `损坏错误来源未知版本日期枚举和小数金额一律拒绝`() {
        val json = BackupCodec.encode(sample()).decodeToString()
        for (text in listOf("{}", "not JSON", json.take(json.length / 2),
            json.replace("VNventoryBackup", "OtherBackup"),
            json.replace("\"schemaVersion\": 2", "\"schemaVersion\": 999"),
            json.replace("2026-10-01", "2026-02-30"),
            json.replace("CUSTOM", "UNKNOWN"),
            json.replace("\"priceMinor\": 50", "\"priceMinor\": 50.5"))) {
            assertThrows(IllegalArgumentException::class.java) { decode(text.encodeToByteArray()) }
        }
        assertEquals(sample(), decode(("\uFEFF" + json).encodeToByteArray()))
        assertEquals(sample(), decode(json.replace("\"format\":", "\"futureField\": true, \"format\":").encodeToByteArray()))
    }

    @Test fun `超大文件有界读取拒绝不尝试解析`() {
        val input = object : InputStream() {
            var remaining = BackupCodec.MAX_BYTES + 1
            override fun read(): Int = if (remaining-- > 0) ' '.code else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(length, remaining)
                bytes.fill(' '.code.toByte(), offset, offset + count)
                remaining -= count
                return count
            }
        }
        val error = assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(input) }
        assertEquals(message(MessageKey.BACKUP_IMPORT_TOO_LARGE), (error as MessageFailure).userMessage)
    }

    @Test fun `负数重复ID悬空关联超额分摊和混币种比例分摊拒绝`() {
        val data = sample()
        invalid(data.copy(defaultCurrency = "jpy"))
        invalid(data.copy(copies = data.copies + data.copies[0]))
        invalid(data.copy(copies = data.copies.map { it.copy(priceMinor = -1) }))
        invalid(data.copy(copies = data.copies.map { it.copy(orderId = 999) }))
        invalid(data.copy(orders = data.orders.map { it.copy(id = 0) }))
        invalid(data.copy(expenses = data.expenses.map { it.copy(orderId = 999) }))
        invalid(data.copy(allocations = data.allocations.map { it.copy(amountMinor = -1) }))
        invalid(data.copy(allocations = data.allocations.map { it.copy(amountMinor = 100) }))
        invalid(data.copy(allocations = data.allocations + data.allocations[0]))
        invalid(data.copy(allocations = listOf(ExpenseAllocationEntity(2, 3, 1))))
        invalid(data.copy(allocations = listOf(ExpenseAllocationEntity(5, 8, 1))))
        invalid(data.copy(copies = data.copies.map { if (it.id == 7L) it.copy(currency = "CNY") else it }))
        invalid(data.copy(copies = data.copies.map { it.copy(priceMinor = Long.MAX_VALUE) }))
    }

    @Test fun `追加重新映射订单收藏费用与手动分摊且保留原数据`() = runTest {
        val settings = settings()
        val repo = repository(settings)
        repo.restore(sample(), replace = false, restoreCurrency = false)
        val first = repo.snapshot()
        val result = repo.restore(decode(BackupCodec.encode(sample())), replace = false, restoreCurrency = true)
        val all = repo.snapshot()
        assertTrue(result.currencyRestored)
        assertEquals("JPY", settings.defaultCurrency.first())
        assertEquals(6, all.copies.size)
        assertEquals(4, all.orders.size)
        assertEquals(8, all.expenses.size)
        assertEquals(4, all.allocations.size)
        assertEquals(first.copies, all.copies.take(first.copies.size))
        val purchases = PurchaseRepository(db, db.purchaseOrderDao(), db.expenseDao(), db.ownedCopyDao(), Dispatchers.Unconfined)
        all.orders.filter { it.title == "骏河屋一批" }.forEach { order ->
            val detail = purchases.observeOrderDetail(order.id).first()!!
            assertEquals(2, detail.copies.size)
            assertTrue(detail.breakdown.issues.isEmpty())
            assertEquals(mapOf("JPY" to 312L, "CNY" to 90L), detail.breakdown.totalsByCurrency)
            assertEquals(mapOf("JPY" to 20L), detail.breakdown.unallocatedTotals)
            assertEquals(listOf(30L, 50L), detail.expenses.single { it.mode == AllocationMode.MANUAL }.allocations.values.sorted())
            assertTrue(detail.expenses.filter { it.mode != AllocationMode.MANUAL }.all { it.allocations.isEmpty() })
        }
    }

    @Test fun `覆盖移除原购买事实保留VNDB缓存与快照且可不恢复配置`() = runTest {
        val settings = settings()
        val repo = repository(settings)
        repo.restore(sample(), false, true)
        db.vnCacheDao().upsertVn(VnCacheEntity("v17", "缓存标题", null, null, null, null, 1))
        val data = sample().copy(orders = emptyList(), copies = listOf(sample().copies.last()), expenses = emptyList(), allocations = emptyList(), defaultCurrency = "USD")
        repo.restore(data, replace = true, restoreCurrency = false)
        val restored = repo.snapshot()
        assertEquals(1, restored.copies.size)
        assertEquals("作品名", restored.copies.single().vnTitle)
        assertEquals("手动版本", restored.copies.single().releaseTitle)
        assertTrue(restored.orders.isEmpty())
        assertTrue(restored.expenses.isEmpty())
        assertTrue(restored.allocations.isEmpty())
        assertEquals("JPY", restored.defaultCurrency)
        assertEquals("缓存标题", db.vnCacheDao().getVn("v17")!!.title)
    }

    @Test fun `追加后总额溢出与覆盖非法数据均不改变数据库或配置`() = runTest {
        val settings = settings()
        val repo = repository(settings)
        val huge = sample().copy(orders = emptyList(), copies = listOf(sample().copies[0].copy(orderId = null, priceMinor = Long.MAX_VALUE)), expenses = emptyList(), allocations = emptyList())
        repo.restore(huge, false, false)
        val before = repo.snapshot()
        try { repo.restore(sample(), false, true); fail("应拒绝全库金额溢出") } catch (_: IllegalArgumentException) { }
        assertEquals(before.copy(exportedAt = 0), repo.snapshot().copy(exportedAt = 0))
        try { repo.restore(sample().copy(allocations = listOf(ExpenseAllocationEntity(5, 3, 101))), true, true); fail("应拒绝非法覆盖") } catch (_: IllegalArgumentException) { }
        assertEquals(before.copy(exportedAt = 0), repo.snapshot().copy(exportedAt = 0))
        assertEquals("CNY", settings.defaultCurrency.first())
    }

    @Test fun `覆盖写入途中失败事务回滚原数据`() = runTest {
        val settings = settings()
        val repo = repository(settings)
        repo.restore(sample(), false, false)
        val before = repo.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON owned_copy BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { repo.restore(sample(), true, true); fail("应模拟数据库写入失败") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before.copy(exportedAt = 0), repo.snapshot().copy(exportedAt = 0))
        assertEquals("CNY", settings.defaultCurrency.first())
    }

    @Test fun `配置写入失败明确返回部分成功而不是诱导重复追加`() = runTest {
        val failingStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("test failure") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("test failure")
        }
        val repo = repository(SettingsRepository(failingStore))
        val result = repo.restore(sample(), false, true)
        assertFalse(result.currencyRestored)
        assertEquals(3, db.ownedCopyDao().getAll().size)
    }

    @Test fun `配置读取失败不能导出默认值或截断目标文件`() = runTest {
        val failingStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("test failure") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("test failure")
        }
        val repo = repository(SettingsRepository(failingStore))
        val files = BackupFileStore(context.contentResolver, repo, Dispatchers.Unconfined)
        val file = File(context.cacheDir, "${UUID.randomUUID()}.json")
        try {
            file.writeText("existing document")
            try { files.export(Uri.fromFile(file)); fail("应拒绝无法读取的配置") } catch (e: IllegalStateException) {
                assertEquals(message(MessageKey.BACKUP_EXPORT_FAILED), (e as MessageFailure).userMessage)
            }
            assertEquals("existing document", file.readText())
        } finally {
            file.delete()
        }
    }

    @Test fun `系统文件导出读取与ViewModel确认取消和恢复流程`() = runTest {
        val settings = settings()
        val repo = repository(settings)
        repo.restore(sample(), false, true)
        val files = BackupFileStore(context.contentResolver, repo, Dispatchers.Unconfined)
        val file = File(context.cacheDir, "${UUID.randomUUID()}.json")
        try {
            val uri = Uri.fromFile(file)
            files.export(uri)
            assertTrue(file.isFile)
            assertEquals(repo.snapshot().copy(exportedAt = 0), files.read(uri).copy(exportedAt = 0))
            val vm = SettingsViewModel(settings, repo, files).also { vmStore.put("settings", it) }
            vm.readBackup(uri)
            vm.backupState.first { !it.busy && it.pendingImport != null }
            vm.restoreBackup(true) // 没有第二次确认，不能覆盖。
            assertEquals(3, db.ownedCopyDao().getAll().size)
            vm.requestReplace()
            assertTrue(vm.backupState.value.replaceConfirmation)
            vm.cancelReplace()
            assertFalse(vm.backupState.value.replaceConfirmation)
            vm.dismissImport()
            assertNull(vm.backupState.value.pendingImport)
            vm.readBackup(uri)
            vm.backupState.first { !it.busy && it.pendingImport != null }
            vm.setRestoreCurrency(false)
            vm.restoreBackup(false)
            vm.backupState.first { !it.busy && it.pendingImport == null && it.feedback != null }
            assertEquals(6, db.ownedCopyDao().getAll().size)
            assertTrue(context.resources.resolve(vm.backupState.value.feedback!!).contains("保持不变"))
        } finally {
            file.delete()
        }
    }

    @Test fun `读取损坏备份有可见错误且不修改现有数据`() = runTest {
        val settings = settings()
        val repo = repository(settings)
        repo.restore(sample(), false, false)
        val files = BackupFileStore(context.contentResolver, repo, Dispatchers.Unconfined)
        val vm = SettingsViewModel(settings, repo, files).also { vmStore.put("settings", it) }
        val file = File(context.cacheDir, "${UUID.randomUUID()}.json")
        try {
            file.writeText("{\"broken\": true}")
            vm.readBackup(Uri.fromFile(file))
            assertEquals(message(MessageKey.BACKUP_INVALID_FILE), vm.actionError.first { it != null })
            vm.backupState.first { !it.busy }
            assertNull(vm.backupState.value.pendingImport)
            assertEquals(3, db.ownedCopyDao().getAll().size)
        } finally {
            file.delete()
        }
    }

    @Test fun `新备份保留店铺候选且老备份不清空本机列表`() = runTest {
        val settings = settings()
        val repo = repository(settings)
        settings.addShopChannel("駿河屋")
        val withShops = sample().copy(shopChannels = listOf("メルカリ", "Sofmap"))
        assertEquals(withShops, decode(BackupCodec.encode(withShops)))
        val root = Json.parseToJsonElement(BackupCodec.encode(sample()).decodeToString()).jsonObject
        val legacy = JsonObject(root + ("settings" to JsonObject(root.getValue("settings").jsonObject - "shopChannels")))
        val old = decode(legacy.toString().encodeToByteArray())
        assertNull(old.shopChannels)
        repo.restore(old, replace = true, restoreCurrency = false)
        assertEquals(listOf("駿河屋"), settings.shopChannels.first())
        repo.restore(withShops, replace = true, restoreCurrency = false, restoreShops = false)
        assertEquals(listOf("駿河屋"), settings.shopChannels.first())
        val restored = repo.restore(withShops, replace = true, restoreCurrency = false, restoreShops = true)
        assertTrue(restored.shopsRestored)
        assertEquals(listOf("メルカリ", "Sofmap"), repo.snapshot().shopChannels)
        assertEquals("店铺", repo.snapshot().copies.first { it.shop != null }.shop)
        repo.restore(withShops.copy(shopChannels = emptyList()), true, false, true)
        assertTrue(settings.shopChannels.first().isEmpty())
    }

    @Test fun `非法店铺候选恢复回滚全部购买事实和配置`() = runTest {
        val settings = settings()
        val repo = repository(settings)
        repo.restore(sample(), false, false)
        settings.addShopChannel("Original")
        val before = repo.snapshot().copy(exportedAt = 0)
        for (names in listOf(listOf(""), listOf(" duplicate "), listOf("Sofmap", "sofmap"))) {
            try { repo.restore(sample().copy(shopChannels = names), true, true); fail("Expected invalid shop list") }
            catch (_: IllegalArgumentException) { }
            assertEquals(before, repo.snapshot().copy(exportedAt = 0))
        }
    }

    @Test fun `店铺配置写入失败返回部分成功不重复导入收藏`() = runTest {
        val failingStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("test failure") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("test failure")
        }
        val result = repository(SettingsRepository(failingStore)).restore(sample().copy(shopChannels = listOf("駿河屋")), false, true, true)
        assertFalse(result.currencyRestored)
        assertFalse(result.shopsRestored)
        assertEquals(3, db.ownedCopyDao().getAll().size)
    }
}
