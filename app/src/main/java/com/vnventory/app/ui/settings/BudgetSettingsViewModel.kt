package com.vnventory.app.ui.settings

import androidx.lifecycle.viewModelScope
import com.vnventory.app.data.repository.BudgetOverview
import com.vnventory.app.data.repository.BudgetRepository
import com.vnventory.app.data.repository.FxBackfillResult
import com.vnventory.app.data.repository.FxFreezer
import com.vnventory.app.data.repository.SettingsRepository
import com.vnventory.app.di.AppContainer
import com.vnventory.app.ui.ActionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class BudgetBackfillUiState(
    val busy: Boolean = false,
    val result: FxBackfillResult? = null,
)

class BudgetSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val budgetRepository: BudgetRepository,
    private val freezer: FxFreezer,
) : ActionViewModel() {
    constructor(container: AppContainer) : this(container.settingsRepository, container.budgetRepository, container.fxFreezer)

    val overview: StateFlow<BudgetOverview> = budgetRepository.observeOverview()
        .catch { reportError(it); emit(BudgetOverview.empty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetOverview.empty())

    private val _backfill = MutableStateFlow(BudgetBackfillUiState())
    val backfill = _backfill.asStateFlow()

    fun setEnabled(value: Boolean) { launchAction { settingsRepository.setBudgetEnabled(value) } }

    fun setCurrency(code: String) { launchAction { settingsRepository.setBudgetCurrency(code) } }

    fun setIncludeFees(value: Boolean) { launchAction { settingsRepository.setBudgetIncludeFees(value) } }

    /** amountMinor 为 null 时清除该年预算。 */
    fun setYearAmount(year: Int, amountMinor: Long?) { launchAction { settingsRepository.setBudgetYearlyAmount(year, amountMinor) } }

    fun backfill() {
        if (_backfill.value.busy) return
        _backfill.value = BudgetBackfillUiState(busy = true)
        launchAction {
            try {
                _backfill.value = BudgetBackfillUiState(result = freezer.backfillAll())
            } finally {
                _backfill.update { it.copy(busy = false) }
            }
        }
    }
}
