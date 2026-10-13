package com.vnventory.app.ui.home

import com.vnventory.app.ui.ActionViewModel
import androidx.lifecycle.viewModelScope
import com.vnventory.app.di.AppContainer
import com.vnventory.app.domain.cost.SpendingStatsEngine
import com.vnventory.app.domain.model.Expense
import com.vnventory.app.domain.model.SpendingDisplayPreferences
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.domain.model.mergeTotals
import com.vnventory.app.domain.cost.HomeSpendingSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 首页已记录消费统计；统一币种时只展示按对应定格汇率换算成功的记录。 */
data class HomeStats(
    val vnCount: Int = 0,
    val copyCount: Int = 0,
    val priceTotals: Map<String, Long> = emptyMap(),
    val shippingTotals: Map<String, Long> = emptyMap(),
    val feeTotals: Map<String, Long> = emptyMap(),
    val taxTotals: Map<String, Long> = emptyMap(),
    val otherTotals: Map<String, Long> = emptyMap(),
    val pricedCopyCount: Int = 0,
    val showPriceStats: Boolean = false,
    val showShelfPrices: Boolean = false,
    val showReleaseNames: Boolean = false,
    val allCopies: List<OwnedCopy> = emptyList(),
    val unifiedSpendingCurrency: Boolean = false,
    val unconvertedSpendingCount: Int = 0,
) {
    /** 全部支出 = 购入成本 + 运费 + 手续费 + 税费 + 其他 */
    val grandTotals: Map<String, Long> = mergeTotals(
            mergeTotals(mergeTotals(mergeTotals(priceTotals, shippingTotals), feeTotals), taxTotals),
            otherTotals,
        )
}

/** 首页封面卡片的显示开关与完整收藏快照（用于稳定盒号）。 */
private data class ShelfDisplay(
    val showShelfPrices: Boolean,
    val showReleaseNames: Boolean,
    val showPriceStats: Boolean,
    val allCopies: List<OwnedCopy>,
)

private data class SpendingDisplay(
    val defaultCurrency: String,
    val preferences: SpendingDisplayPreferences,
)

private data class HomeDisplay(
    val shelf: ShelfDisplay,
    val spending: SpendingDisplay,
    val expenses: List<Expense>,
)

/** 首页年度预算卡（仅在预算开启后显示）。 */
data class HomeBudget(
    val enabled: Boolean,
    val year: Int,
    val spentMinor: Long,
    val amountMinor: Long?,
    val currency: String,
    val issueCount: Int,
)

class HomeViewModel(container: AppContainer) : ActionViewModel() {

    private val collectionRepository = container.collectionRepository
    private val purchaseRepository = container.purchaseRepository
    private val settingsRepository = container.settingsRepository

    private val shelfDisplay = combine(
        container.settingsRepository.showShelfPrices,
        container.settingsRepository.showShelfReleaseNames,
        container.settingsRepository.showPriceStats,
        collectionRepository.observeCollection(com.vnventory.app.domain.model.CollectionQuery()),
    ) { shelf, releaseNames, stats, copies -> ShelfDisplay(shelf, releaseNames, stats, copies) }

    private val spendingDisplay = combine(
        container.settingsRepository.defaultCurrency,
        container.settingsRepository.spendingDisplayPreferences,
    ) { defaultCurrency, preferences -> SpendingDisplay(defaultCurrency, preferences) }

    private val homeDisplay = combine(
        shelfDisplay,
        spendingDisplay,
        purchaseRepository.observeAllExpenses(),
    ) { shelf, spending, expenses -> HomeDisplay(shelf, spending, expenses) }

    val stats: StateFlow<HomeStats> = combine(
        collectionRepository.observeDistinctVnCount(),
        combine(collectionRepository.observeCopyCount(), collectionRepository.observePricedCopyCount()) { all, priced -> all to priced },
        homeDisplay,
    ) { vnCount, (copyCount, pricedCount), display ->
        val unifiedCurrency = display.spending.preferences.unifiedCurrency
        val targetCurrency = if (unifiedCurrency) {
            display.spending.preferences.currency ?: display.spending.defaultCurrency
        } else null
        val spending = SpendingStatsEngine.summarize(display.shelf.allCopies, display.expenses, targetCurrency)
        HomeStats(
            vnCount = vnCount,
            copyCount = copyCount,
            priceTotals = spending.goods,
            shippingTotals = spending.shipping,
            feeTotals = spending.fees,
            taxTotals = spending.taxes,
            otherTotals = spending.other,
            pricedCopyCount = pricedCount,
            showShelfPrices = display.shelf.showShelfPrices,
            showPriceStats = display.shelf.showPriceStats,
            showReleaseNames = display.shelf.showReleaseNames,
            allCopies = display.shelf.allCopies,
            unifiedSpendingCurrency = unifiedCurrency,
            unconvertedSpendingCount = spending.unconvertedCount,
        )
    }.catch { reportError(it); emit(HomeStats()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeStats())

    val recentCopies: StateFlow<List<OwnedCopy>> = collectionRepository.observeRecent(limit = 8)
        .catch { reportError(it); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val budget: StateFlow<HomeBudget?> = container.budgetRepository.observeOverview()
        .map<com.vnventory.app.data.repository.BudgetOverview, HomeBudget?> { overview ->
            HomeBudget(
                enabled = overview.preferences.enabled,
                year = overview.currentYear,
                spentMinor = overview.summary.totalForYear(overview.currentYear),
                amountMinor = overview.preferences.yearlyAmounts[overview.currentYear],
                currency = overview.targetCurrency,
                issueCount = overview.summary.issues.size,
            )
        }
        .catch { reportError(it); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 首页消费汇总独立于预算开关与限额。 */
    val homeSpending: StateFlow<HomeSpendingSummary> = container.budgetRepository.observeHomeSpending()
        .catch { reportError(it); emit(HomeSpendingSummary.empty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSpendingSummary.empty())

    val showHomeYearSpending: StateFlow<Boolean> = settingsRepository.showHomeYearSpending
        .catch { reportError(it); emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setShowHomeYearSpending(value: Boolean) {
        launchAction { settingsRepository.setShowHomeYearSpending(value) }
    }
}
