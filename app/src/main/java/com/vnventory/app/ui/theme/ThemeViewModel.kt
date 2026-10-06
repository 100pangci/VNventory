package com.vnventory.app.ui.theme

import androidx.lifecycle.viewModelScope
import com.vnventory.app.data.repository.SettingsRepository
import com.vnventory.app.di.AppContainer
import com.vnventory.app.domain.model.AppearancePreferences
import com.vnventory.app.domain.model.TitleDisplayMode
import com.vnventory.app.ui.ActionViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

class ThemeViewModel(settings: SettingsRepository) : ActionViewModel() {
    constructor(container: AppContainer) : this(container.settingsRepository)

    val showCopyNumbers = settings.showCopyNumbers
        .catch { reportError(it); emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val titleDisplayMode = settings.titleDisplayMode
        .catch { reportError(it); emit(TitleDisplayMode.ORIGINAL) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TitleDisplayMode.ORIGINAL)

    // 等首次读取完成再显示内容，避免强制深色的用户先看到一帧浅色页面。
    val appearance: StateFlow<AppearancePreferences?> = settings.appearance
        .catch { reportError(it); emit(AppearancePreferences()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
