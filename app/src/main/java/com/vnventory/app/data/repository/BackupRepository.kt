package com.vnventory.app.data.repository

import androidx.room.withTransaction
import com.vnventory.app.data.backup.BackupData
import com.vnventory.app.data.local.VNventoryDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class BackupRestoreResult(val currencyRestored: Boolean)

class BackupRepository(
    private val database: VNventoryDatabase,
    private val settings: SettingsRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun snapshot(): BackupData = withContext(io) {
        val currency = settings.getDefaultCurrency()
        database.withTransaction {
            BackupData(
                exportedAt = System.currentTimeMillis(),
                defaultCurrency = currency,
                orders = database.backupDao().getOrders(),
                copies = database.ownedCopyDao().getAll().sortedBy { it.id },
                expenses = database.expenseDao().getAll().sortedBy { it.id },
                allocations = database.backupDao().getAllocations(),
            )
        }
    }

    suspend fun restore(data: BackupData, replace: Boolean, restoreCurrency: Boolean): BackupRestoreResult = withContext(io) {
        database.withTransaction {
            data.validate()
            if (replace) {
                database.backupDao().deleteCopies()
                database.backupDao().deleteOrders()
            }
            // 无论追加还是覆盖均分配新 ID，避免与本机已有记录冲突；所有关联同步映射。
            val orderIds = data.orders.associate { it.id to database.purchaseOrderDao().insert(it.copy(id = 0)) }
            val copyIds = data.copies.associate {
                it.id to database.ownedCopyDao().insert(it.copy(id = 0, orderId = it.orderId?.let(orderIds::getValue)))
            }
            val expenseIds = data.expenses.associate {
                it.id to database.expenseDao().insert(it.copy(id = 0, orderId = orderIds.getValue(it.orderId)))
            }
            database.expenseDao().upsertAllocations(data.allocations.map {
                it.copy(expenseId = expenseIds.getValue(it.expenseId), ownedCopyId = copyIds.getValue(it.ownedCopyId))
            })
            // 追加后也要检查全库合计；失败时插入、覆盖删除全部回滚。
            LocalRules.totals(database)
        }
        // Room 与 DataStore 不能组成一个事务。数据已提交后，配置失败必须明确反馈，
        // 不能报成“全部失败”导致用户重试追加并生成重复记录。
        withContext(NonCancellable) {
            val currencyRestored = if (!restoreCurrency) false else try {
                settings.setDefaultCurrency(data.defaultCurrency)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            BackupRestoreResult(currencyRestored)
        }
    }
}
