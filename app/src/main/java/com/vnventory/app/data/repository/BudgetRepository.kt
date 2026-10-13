package com.vnventory.app.data.repository

import com.vnventory.app.domain.cost.BudgetEngine
import com.vnventory.app.domain.cost.BudgetRecordInput
import com.vnventory.app.domain.cost.BudgetRecordKind
import com.vnventory.app.domain.cost.BudgetSummary
import com.vnventory.app.domain.cost.HomeSpendingEngine
import com.vnventory.app.domain.cost.HomeSpendingSummary
import com.vnventory.app.domain.model.BudgetPreferences
import com.vnventory.app.domain.model.CollectionQuery
import com.vnventory.app.domain.model.Expense
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.domain.model.OrderSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate

/**
 * 预算总览：设置 + 折算汇总 + 原始记录映射（供未计入清单显示与跳转）。
 *
 * 归年日期：收藏 = 购买日期，缺失时用所属订单的下单日期；费用 = 所属订单的下单日期。
 * 口径为「仅本体价」时不传入费用记录，费用不计入也不列为未计入。
 */
data class BudgetOverview(
    val preferences: BudgetPreferences,
    val targetCurrency: String,
    val summary: BudgetSummary,
    val copies: Map<Long, OwnedCopy>,
    val expenses: Map<Long, Expense>,
    val orderTitles: Map<Long, String>,
    val currentYear: Int,
) {
    companion object {
        fun empty(currentYear: Int = LocalDate.now().year) = BudgetOverview(
            preferences = BudgetPreferences(),
            targetCurrency = SettingsRepository.FALLBACK_CURRENCY,
            summary = BudgetSummary(SettingsRepository.FALLBACK_CURRENCY, emptyMap(), emptyList()),
            copies = emptyMap(),
            expenses = emptyMap(),
            orderTitles = emptyMap(),
            currentYear = currentYear,
        )
    }
}

class BudgetRepository(
    private val collectionRepository: CollectionRepository,
    private val purchaseRepository: PurchaseRepository,
    private val settingsRepository: SettingsRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val today: () -> LocalDate = LocalDate::now,
) {

    fun observeOverview(): Flow<BudgetOverview> = combine(
        collectionRepository.observeCollection(CollectionQuery()),
        purchaseRepository.observeAllExpenses(),
        purchaseRepository.observeOrders(),
        settingsRepository.budgetPreferences,
        settingsRepository.defaultCurrency,
    ) { copies, expenses, orders, preferences, fallbackCurrency ->
        val target = preferences.currency ?: fallbackCurrency
        val allRecords = records(copies, expenses, orders)
        val records = if (preferences.includeFees) allRecords else allRecords.filter { it.kind == BudgetRecordKind.COPY }
        BudgetOverview(
            preferences = preferences,
            targetCurrency = target,
            summary = BudgetEngine.summarize(records, target),
            copies = copies.associateBy { it.id },
            expenses = expenses.associateBy { it.id },
            orderTitles = orders.associate { it.order.id to it.order.title },
            currentYear = today().year,
        )
    }.flowOn(io)

    /**
     * 首页统计始终包含全部费用，与“总金额”的现有口径一致；不受预算开关、预算金额或
     * “预算是否统计费用”影响。目标币种沿用汇率定格目标（结算币种，否则默认货币）。
     */
    fun observeHomeSpending(): Flow<HomeSpendingSummary> = combine(
        collectionRepository.observeCollection(CollectionQuery()),
        purchaseRepository.observeAllExpenses(),
        purchaseRepository.observeOrders(),
        settingsRepository.fxTargetCurrency,
    ) { copies, expenses, orders, target ->
        HomeSpendingEngine.summarize(records(copies, expenses, orders), target, today().year)
    }.flowOn(io)

    private fun records(
        copies: List<OwnedCopy>,
        expenses: List<Expense>,
        orders: List<OrderSummary>,
    ): List<BudgetRecordInput> {
        val orderDates = orders.associate { it.order.id to it.order.orderDate }
        return buildList {
            copies.forEach { copy ->
                add(
                    BudgetRecordInput(
                        kind = BudgetRecordKind.COPY,
                        id = copy.id,
                        amountMinor = copy.priceMinor,
                        currency = copy.currency,
                        date = copy.purchaseDate ?: copy.orderId?.let(orderDates::get),
                        orderId = copy.orderId,
                        fxRateScaled = copy.fxRateScaled,
                        fxToCurrency = copy.fxToCurrency,
                    )
                )
            }
            expenses.forEach { expense ->
                add(
                    BudgetRecordInput(
                        kind = BudgetRecordKind.EXPENSE,
                        id = expense.id,
                        amountMinor = expense.amountMinor,
                        currency = expense.currency,
                        date = orderDates[expense.orderId],
                        orderId = expense.orderId,
                        fxRateScaled = expense.fxRateScaled,
                        fxToCurrency = expense.fxToCurrency,
                    )
                )
            }
        }
    }
}
