package com.vnventory.app.ui.settings

import android.net.Uri
import com.vnventory.app.ui.ActionViewModel
import androidx.lifecycle.viewModelScope
import com.vnventory.app.data.backup.BackupData
import com.vnventory.app.data.backup.BackupFileStore
import com.vnventory.app.data.repository.BackupRepository
import com.vnventory.app.data.repository.SettingsRepository
import com.vnventory.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class BackupUiState(
    val busy: Boolean = false,
    val progress: String = "",
    val feedback: String? = null,
    val pendingImport: BackupData? = null,
    val replaceConfirmation: Boolean = false,
    val restoreCurrency: Boolean = true,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val backupFiles: BackupFileStore,
) : ActionViewModel() {
    constructor(container: AppContainer) : this(container.settingsRepository, container.backupRepository, container.backupFileStore)

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState = _backupState.asStateFlow()

    val defaultCurrency: StateFlow<String> = settingsRepository.defaultCurrency
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsRepository.FALLBACK_CURRENCY,
        )

    fun setDefaultCurrency(code: String) {
        launchAction { settingsRepository.setDefaultCurrency(code) }
    }

    fun exportBackup(uri: Uri) = backupAction("正在导出备份…") {
        backupFiles.export(uri)
        _backupState.update { it.copy(feedback = "备份已保存") }
    }

    fun readBackup(uri: Uri) = backupAction("正在检查备份…") {
        val backup = backupFiles.read(uri)
        _backupState.update { it.copy(pendingImport = backup, replaceConfirmation = false, restoreCurrency = true) }
    }

    fun setRestoreCurrency(value: Boolean) {
        if (!_backupState.value.busy) _backupState.update { it.copy(restoreCurrency = value) }
    }

    fun dismissImport() {
        if (!_backupState.value.busy) _backupState.update { it.copy(pendingImport = null, replaceConfirmation = false) }
    }

    fun requestReplace() {
        if (!_backupState.value.busy && _backupState.value.pendingImport != null) {
            _backupState.update { it.copy(replaceConfirmation = true) }
        }
    }

    fun cancelReplace() {
        if (!_backupState.value.busy) _backupState.update { it.copy(replaceConfirmation = false) }
    }

    fun restoreBackup(replace: Boolean) {
        val state = _backupState.value
        val backup = state.pendingImport ?: return
        if (replace && !state.replaceConfirmation) return
        backupAction("正在恢复备份…") {
            val result = backupRepository.restore(backup, replace, state.restoreCurrency)
            val feedback = if (state.restoreCurrency && !result.currencyRestored) {
                "收藏和订单已恢复，但默认货币恢复失败；请在偏好设置中手动调整，不要重复追加导入。"
            } else if (state.restoreCurrency) {
                "备份已恢复，默认货币已设为 ${backup.defaultCurrency}"
            } else {
                "备份已恢复，默认货币保持不变"
            }
            _backupState.update { it.copy(pendingImport = null, replaceConfirmation = false, feedback = feedback) }
        }
    }

    private fun backupAction(progress: String, block: suspend () -> Unit) {
        if (_backupState.value.busy) return
        _backupState.update { it.copy(busy = true, progress = progress, feedback = null) }
        launchAction {
            try {
                block()
            } finally {
                _backupState.update { it.copy(busy = false, progress = "") }
            }
        }
    }
}
