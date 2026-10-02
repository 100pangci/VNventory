package com.vnventory.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnventory.app.data.repository.SettingsRepository
import com.vnventory.app.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(container: AppContainer) : ViewModel() {

    private val settingsRepository = container.settingsRepository

    val defaultCurrency: StateFlow<String> = settingsRepository.defaultCurrency
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsRepository.FALLBACK_CURRENCY,
        )

    fun setDefaultCurrency(code: String) {
        viewModelScope.launch { settingsRepository.setDefaultCurrency(code) }
    }
}
