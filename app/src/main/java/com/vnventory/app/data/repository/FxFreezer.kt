package com.vnventory.app.data.repository

import androidx.room.withTransaction
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.local.entity.ExpenseEntity
import com.vnventory.app.data.local.entity.OwnedCopyEntity
import com.vnventory.app.data.remote.fx.FxFetchResult
import com.vnventory.app.data.remote.fx.FxRateClient
import com.vnventory.app.domain.cost.BudgetRecordKind
import com.vnventory.app.domain.model.Fx
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import com.vnventory.app.domain.text.requireMessage
import com.vnventory.app.domain.text.requireNotNullMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** 单条记录的定格结果。 */
enum class FxFreezeOutcome { FROZEN, PENDING, UNSUPPORTED, FAILED, SKIPPED }

/** 批量定格统计（显式「补全历史」与自动重试共用）。 */
data class FxBackfillResult(
    val succeeded: Int = 0,
    val pending: Int = 0,
    val unsupported: Int = 0,
    val failed: Int = 0,
) {
    val total: Int get() = succeeded + pending + unsupported + failed
}

/**
 * 汇率定格服务。
 *
 * - 保存 / 编辑外币记录后调用 [requestCopiesFreeze] / [requestExpenseFreeze]，
 *   后台执行、不阻塞保存、失败不影响本地功能（与封面预热相同的语义）。
 * - [requestRecentRetry] 在应用启动 / 回到前台时静默重试最近几天的待定格记录。
 * - [backfillAll] 是用户显式动作：把全部未定格 / 目标币种不符的记录批量定格。
 */
class FxFreezer(
    private val database: VNventoryDatabase,
    private val settingsRepository: SettingsRepository,
    private val client: FxRateClient,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val today: () -> LocalDate = LocalDate::now,
) {

    /** 后台定格；返回 Job 便于调用方在测试中等待，生产代码忽略返回值。 */
    fun requestCopiesFreeze(copyIds: List<Long>): Job? {
        if (copyIds.isEmpty()) return null
        return scope.launch {
            try {
                freezeCopies(copyIds)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 后台定格失败静默保留待定格状态，由重试或手动填写补上。
            }
        }
    }

    fun requestExpenseFreeze(expenseId: Long): Job? = requestExpensesFreeze(listOf(expenseId))

    fun requestExpensesFreeze(expenseIds: List<Long>): Job? {
        if (expenseIds.isEmpty()) return null
        return scope.launch {
            try {
                freezeExpenses(expenseIds)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 同上。
            }
        }
    }

    /** 应用启动 / 回到前台：只重试最近窗口内的待定格记录，不请求历史数据。 */
    fun requestRecentRetry(): Job = scope.launch {
        try {
            freezePending(onlyIds = null, windowDays = AUTO_RETRY_WINDOW_DAYS, includeStale = false, limit = AUTO_RETRY_LIMIT)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 同上。
        }
    }

    /** 显式补全：未定格 + 目标币种不符。 */
    suspend fun backfillAll(): FxBackfillResult = withContext(io) {
        freezePending(onlyIds = null, windowDays = null, includeStale = true, limit = null)
    }

    /** 单条重新获取；[includeStale] 为 true 时同时重定格目标币种不符的记录。 */
    suspend fun freezeRecord(kind: BudgetRecordKind, id: Long, includeStale: Boolean = false): FxFreezeOutcome = withContext(io) {
        val result = freezePending(onlyIds = mapOf(kind to listOf(id)), windowDays = null, includeStale = includeStale, limit = null)
        when {
            result.succeeded > 0 -> FxFreezeOutcome.FROZEN
            result.pending > 0 -> FxFreezeOutcome.PENDING
            result.unsupported > 0 -> FxFreezeOutcome.UNSUPPORTED
            result.failed > 0 -> FxFreezeOutcome.FAILED
            else -> FxFreezeOutcome.SKIPPED
        }
    }

    /** 手动写入定格快照（当前目标币种）。 */
    suspend fun setManualSnapshot(kind: BudgetRecordKind, id: Long, rateScaled: Long, date: LocalDate) = withContext(io) {
        requireMessage(rateScaled > 0) { message(MessageKey.FX_RATE_INVALID) }
        val target = settingsRepository.getFxTargetCurrency()
        database.withTransaction {
            when (kind) {
                BudgetRecordKind.COPY -> {
                    val entity = requireNotNullMessage(database.ownedCopyDao().getById(id)) { message(MessageKey.COPY_MISSING) }
                    requireMessage(entity.currency != target) { message(MessageKey.FX_SNAPSHOT_INVALID) }
                    database.ownedCopyDao().update(
                        entity.copy(
                            fxRateScaled = rateScaled, fxDate = date, fxToCurrency = target, fxSource = Fx.SOURCE_MANUAL,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }

                BudgetRecordKind.EXPENSE -> {
                    val entity = requireNotNullMessage(database.expenseDao().getById(id)) { message(MessageKey.EXPENSE_MISSING) }
                    requireMessage(entity.currency != target) { message(MessageKey.FX_SNAPSHOT_INVALID) }
                    database.expenseDao().update(
                        entity.copy(fxRateScaled = rateScaled, fxDate = date, fxToCurrency = target, fxSource = Fx.SOURCE_MANUAL)
                    )
                }
            }
        }
    }

    /** 清除定格快照，回到未定格状态。 */
    suspend fun clearSnapshot(kind: BudgetRecordKind, id: Long) = withContext(io) {
        database.withTransaction {
            when (kind) {
                BudgetRecordKind.COPY -> {
                    val entity = requireNotNullMessage(database.ownedCopyDao().getById(id)) { message(MessageKey.COPY_MISSING) }
                    database.ownedCopyDao().update(
                        entity.copy(fxRateScaled = null, fxDate = null, fxToCurrency = null, fxSource = null, updatedAt = System.currentTimeMillis())
                    )
                }

                BudgetRecordKind.EXPENSE -> {
                    val entity = requireNotNullMessage(database.expenseDao().getById(id)) { message(MessageKey.EXPENSE_MISSING) }
                    database.expenseDao().update(
                        entity.copy(fxRateScaled = null, fxDate = null, fxToCurrency = null, fxSource = null)
                    )
                }
            }
        }
    }

    private suspend fun freezeCopies(copyIds: List<Long>): FxBackfillResult =
        freezePending(onlyIds = mapOf(BudgetRecordKind.COPY to copyIds), windowDays = null, includeStale = false, limit = null)

    private suspend fun freezeExpenses(expenseIds: List<Long>): FxBackfillResult =
        freezePending(onlyIds = mapOf(BudgetRecordKind.EXPENSE to expenseIds), windowDays = null, includeStale = false, limit = null)

    private data class FreezeTask(
        val kind: BudgetRecordKind,
        val recordId: Long,
        val fromCurrency: String,
        val targetCurrency: String,
        val date: LocalDate,
    )

    private suspend fun freezePending(
        onlyIds: Map<BudgetRecordKind, List<Long>>?,
        windowDays: Long?,
        includeStale: Boolean,
        limit: Int?,
    ): FxBackfillResult {
        val target = settingsRepository.getFxTargetCurrency()
        val tasks = buildTasks(target, onlyIds, windowDays, includeStale).let { list ->
            if (limit != null) list.take(limit) else list
        }
        if (tasks.isEmpty()) return FxBackfillResult()
        var result = FxBackfillResult()
        tasks.groupBy { Triple(it.fromCurrency, it.date, it.targetCurrency) }.forEach { (key, group) ->
            val (from, date, targetCurrency) = key
            when (val fetched = client.fetch(from, targetCurrency, date)) {
                is FxFetchResult.Success -> {
                    var succeeded = 0
                    group.forEach { task -> if (applySnapshot(task, fetched.rateScaled, fetched.rateDate, fetched.source)) succeeded++ }
                    result = result.copy(succeeded = result.succeeded + succeeded)
                }

                FxFetchResult.NotAvailable -> result = result.copy(pending = result.pending + group.size)
                FxFetchResult.Unsupported -> result = result.copy(unsupported = result.unsupported + group.size)
                FxFetchResult.Failed -> result = result.copy(failed = result.failed + group.size)
            }
        }
        return result
    }

    private suspend fun buildTasks(
        target: String,
        onlyIds: Map<BudgetRecordKind, List<Long>>?,
        windowDays: Long?,
        includeStale: Boolean,
    ): List<FreezeTask> {
        val orders = database.purchaseOrderDao().getAll().associate { it.id to it.orderDate }
        val copyFilter = onlyIds?.get(BudgetRecordKind.COPY)?.toSet()
        val expenseFilter = onlyIds?.get(BudgetRecordKind.EXPENSE)?.toSet()
        val tasks = mutableListOf<FreezeTask>()
        if (onlyIds == null || copyFilter != null) {
            database.ownedCopyDao().getAll().forEach { entity ->
                if (copyFilter != null && entity.id !in copyFilter) return@forEach
                copyTask(entity, target, orders, includeStale, windowDays)?.let(tasks::add)
            }
        }
        if (onlyIds == null || expenseFilter != null) {
            database.expenseDao().getAll().forEach { entity ->
                if (expenseFilter != null && entity.id !in expenseFilter) return@forEach
                expenseTask(entity, target, orders, includeStale, windowDays)?.let(tasks::add)
            }
        }
        return tasks
    }

    private fun copyTask(
        entity: OwnedCopyEntity,
        target: String,
        orderDates: Map<Long, LocalDate?>,
        includeStale: Boolean,
        windowDays: Long?,
    ): FreezeTask? {
        if (entity.currency == target) return null
        val stale = entity.fxToCurrency != null && entity.fxToCurrency != target
        if (entity.fxRateScaled != null && !(includeStale && stale)) return null
        val date = if (stale) entity.fxDate ?: entity.purchaseDate ?: entity.orderId?.let(orderDates::get)
        else entity.purchaseDate ?: entity.orderId?.let(orderDates::get)
        return date?.let { validateWindow(it, windowDays) }?.let { FreezeTask(BudgetRecordKind.COPY, entity.id, entity.currency, target, it) }
    }

    private fun expenseTask(
        entity: ExpenseEntity,
        target: String,
        orderDates: Map<Long, LocalDate?>,
        includeStale: Boolean,
        windowDays: Long?,
    ): FreezeTask? {
        if (entity.currency == target) return null
        val stale = entity.fxToCurrency != null && entity.fxToCurrency != target
        if (entity.fxRateScaled != null && !(includeStale && stale)) return null
        val date = if (stale) entity.fxDate ?: orderDates[entity.orderId]
        else orderDates[entity.orderId]
        return date?.let { validateWindow(it, windowDays) }?.let { FreezeTask(BudgetRecordKind.EXPENSE, entity.id, entity.currency, target, it) }
    }

    private fun validateWindow(date: LocalDate, windowDays: Long?): LocalDate? {
        if (date > today()) return null
        if (windowDays != null && date < today().minusDays(windowDays)) return null
        return date
    }

    private suspend fun applySnapshot(task: FreezeTask, rateScaled: Long, rateDate: LocalDate, source: String): Boolean =
        database.withTransaction {
            when (task.kind) {
                BudgetRecordKind.COPY -> {
                    val entity = database.ownedCopyDao().getById(task.recordId) ?: return@withTransaction false
                    if (entity.currency != task.fromCurrency) return@withTransaction false
                    if (entity.fxRateScaled != null && entity.fxToCurrency == task.targetCurrency) return@withTransaction false
                    database.ownedCopyDao().update(
                        entity.copy(
                            fxRateScaled = rateScaled, fxDate = rateDate, fxToCurrency = task.targetCurrency, fxSource = source,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                    true
                }

                BudgetRecordKind.EXPENSE -> {
                    val entity = database.expenseDao().getById(task.recordId) ?: return@withTransaction false
                    if (entity.currency != task.fromCurrency) return@withTransaction false
                    if (entity.fxRateScaled != null && entity.fxToCurrency == task.targetCurrency) return@withTransaction false
                    database.expenseDao().update(
                        entity.copy(fxRateScaled = rateScaled, fxDate = rateDate, fxToCurrency = task.targetCurrency, fxSource = source)
                    )
                    true
                }
            }
        }

    companion object {
        /** 自动重试只覆盖最近几天，避免升级后对旧收藏批量发起请求。 */
        const val AUTO_RETRY_WINDOW_DAYS = 3L
        const val AUTO_RETRY_LIMIT = 40
    }
}
