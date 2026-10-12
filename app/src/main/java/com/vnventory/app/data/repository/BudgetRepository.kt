package com.vnventory.app.data.repository

import com.vnventory.app.domain.cost.BudgetEngine
import com.vnventory.app.domain.cost.BudgetRecordInput
import com.vnventory.app.domain.cost.BudgetRecordKind
import com.vnventory.app.domain.cost.BudgetSummary
import com.vnventory.app.domain.model.BudgetPreferences
import com.vnventory.app.domain.model.CollectionQuery
import com.vnventory.app.domain.model.Expense
import com.vnventory.app.domain.model.OwnedCopy
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
        val orderDates = orders.associate { it.order.id to it.order.orderDate }
        val records = buildList {
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
            if (preferences.includeFees) {
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
}
