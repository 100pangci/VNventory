package com.vnventory.app.ui.budget

import androidx.lifecycle.viewModelScope
import com.vnventory.app.data.repository.BudgetOverview
import com.vnventory.app.data.repository.BudgetRepository
import com.vnventory.app.data.repository.FxBackfillResult
import com.vnventory.app.data.repository.FxFreezeOutcome
import com.vnventory.app.data.repository.FxFreezer
import com.vnventory.app.di.AppContainer
import com.vnventory.app.domain.cost.BudgetRecordKind
import com.vnventory.app.ui.ActionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class BudgetDetailUiState(
    val overview: BudgetOverview = BudgetOverview.empty(),
    val selectedYear: Int = LocalDate.now().year,
    val backfillBusy: Boolean = false,
    val backfillResult: FxBackfillResult? = null,
    val lastOutcome: FxFreezeOutcome? = null,
)

class BudgetDetailViewModel(
    private val budgetRepository: BudgetRepository,
    private val freezer: FxFreezer,
) : ActionViewModel() {
    constructor(container: AppContainer) : this(container.budgetRepository, container.fxFreezer)

    private val selectedYear = MutableStateFlow(LocalDate.now().year)
    private val backfillBusy = MutableStateFlow(false)
    private val backfillResult = MutableStateFlow<FxBackfillResult?>(null)
    private val lastOutcome = MutableStateFlow<FxFreezeOutcome?>(null)

    val uiState: StateFlow<BudgetDetailUiState> = combine(
        budgetRepository.observeOverview().catch { reportError(it); emit(BudgetOverview.empty()) },
        selectedYear,
        backfillBusy,
        backfillResult,
        lastOutcome,
    ) { overview, year, busy, result, outcome ->
        BudgetDetailUiState(overview, year, busy, result, outcome)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetDetailUiState())

    fun selectYear(year: Int) { selectedYear.value = year }

    fun dismissOutcome() { lastOutcome.value = null }

    fun retry(kind: BudgetRecordKind, id: Long, includeStale: Boolean) {
        launchAction { lastOutcome.value = freezer.freezeRecord(kind, id, includeStale) }
    }

    fun setManual(kind: BudgetRecordKind, id: Long, rateScaled: Long, date: LocalDate) {
        launchAction {
            freezer.setManualSnapshot(kind, id, rateScaled, date)
            lastOutcome.value = FxFreezeOutcome.FROZEN
        }
    }

    fun clear(kind: BudgetRecordKind, id: Long) {
        launchAction { freezer.clearSnapshot(kind, id) }
    }

    fun backfill() {
        if (backfillBusy.value) return
        backfillBusy.value = true
        launchAction {
            try {
                backfillResult.value = freezer.backfillAll()
            } finally {
                backfillBusy.value = false
            }
        }
    }
}
