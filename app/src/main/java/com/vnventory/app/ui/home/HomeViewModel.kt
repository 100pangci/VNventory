package com.vnventory.app.ui.home

import com.vnventory.app.ui.ActionViewModel
import androidx.lifecycle.viewModelScope
import com.vnventory.app.di.AppContainer
import com.vnventory.app.domain.model.ExpenseCategory
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.domain.model.mergeTotals
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/** 首页统计（金额按币种分组，不做隐式换算） */
data class HomeStats(
    val vnCount: Int = 0,
    val copyCount: Int = 0,
    val priceTotals: Map<String, Long> = emptyMap(),
    val shippingTotals: Map<String, Long> = emptyMap(),
    val feeTotals: Map<String, Long> = emptyMap(),
    val taxTotals: Map<String, Long> = emptyMap(),
    val otherTotals: Map<String, Long> = emptyMap(),
) {
    /** 全部支出 = 购入成本 + 运费 + 手续费 + 税费 + 其他 */
    val grandTotals: Map<String, Long> = mergeTotals(
            mergeTotals(mergeTotals(mergeTotals(priceTotals, shippingTotals), feeTotals), taxTotals),
            otherTotals,
        )
}

class HomeViewModel(container: AppContainer) : ActionViewModel() {

    private val collectionRepository = container.collectionRepository
    private val purchaseRepository = container.purchaseRepository

    val stats: StateFlow<HomeStats> = combine(
        collectionRepository.observeDistinctVnCount(),
        collectionRepository.observeCopyCount(),
        collectionRepository.observePriceTotals(),
        purchaseRepository.observeExpenseCategoryTotals(),
    ) { vnCount, copyCount, priceTotals, categoryTotals ->
        HomeStats(
            vnCount = vnCount,
            copyCount = copyCount,
            priceTotals = priceTotals.associate { it.currency to it.total },
            shippingTotals = categoryTotals.totalsFor(ExpenseCategory.SHIPPING),
            feeTotals = categoryTotals.totalsFor(ExpenseCategory.FEE),
            taxTotals = categoryTotals.totalsFor(ExpenseCategory.TAX),
            otherTotals = categoryTotals.totalsFor(ExpenseCategory.OTHER),
        )
    }.catch { reportError(it); emit(HomeStats()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeStats())

    val recentCopies: StateFlow<List<OwnedCopy>> = collectionRepository.observeRecent(limit = 8)
        .catch { reportError(it); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private fun List<com.vnventory.app.data.local.dao.CategoryTotal>.totalsFor(
    category: ExpenseCategory,
): Map<String, Long> = filter { it.category == category.name }.associate { it.currency to it.total }
