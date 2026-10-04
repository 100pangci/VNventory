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
import kotlinx.coroutines.flow.catch
import com.vnventory.app.domain.text.Message
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message

data class BackupUiState(
    val busy: Boolean = false,
    val progress: Message = Message.Literal(""),
    val feedback: Message? = null,
    val pendingImport: BackupData? = null,
    val replaceConfirmation: Boolean = false,
    val restoreCurrency: Boolean = true,
    val restoreShops: Boolean = true,
)

data class ShopEditorState(val open: Boolean = false, val original: String? = null, val name: String = "")

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val backupFiles: BackupFileStore,
) : ActionViewModel() {
    constructor(container: AppContainer) : this(container.settingsRepository, container.backupRepository, container.backupFileStore)

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState = _backupState.asStateFlow()
    private val _shopEditor = MutableStateFlow(ShopEditorState())
    val shopEditor = _shopEditor.asStateFlow()
    private val _shopsBusy = MutableStateFlow(false)
    val shopsBusy = _shopsBusy.asStateFlow()
    val shopChannels = settingsRepository.shopChannels
        .catch { reportError(it); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openShopEditor(original: String? = null) {
        if (_shopsBusy.value) return
        clearError()
        _shopEditor.value = ShopEditorState(open = true, original = original, name = original.orEmpty())
    }

    fun onShopNameChange(value: String) {
        if (!_shopsBusy.value) _shopEditor.update { it.copy(name = value) }
    }

    fun dismissShopEditor() {
        if (!_shopsBusy.value) { clearError(); _shopEditor.value = ShopEditorState() }
    }

    fun saveShop() {
        val editor = _shopEditor.value
        if (!editor.open || _shopsBusy.value) return
        _shopsBusy.value = true
        launchAction {
            try {
                if (editor.original == null) settingsRepository.addShopChannel(editor.name)
                else settingsRepository.renameShopChannel(editor.original, editor.name)
                _shopEditor.value = ShopEditorState()
            } finally { _shopsBusy.value = false }
        }
    }

    fun deleteShop(name: String, onDeleted: () -> Unit) {
        if (_shopsBusy.value) return
        _shopsBusy.value = true
        launchAction {
            try { settingsRepository.removeShopChannel(name); onDeleted() }
            finally { _shopsBusy.value = false }
        }
    }

    val defaultCurrency: StateFlow<String> = settingsRepository.defaultCurrency
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsRepository.FALLBACK_CURRENCY,
        )

    val showShelfPrices = settingsRepository.showShelfPrices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val showPriceStats = settingsRepository.showPriceStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setShowShelfPrices(value: Boolean) { launchAction { settingsRepository.setShowShelfPrices(value) } }
    fun setShowPriceStats(value: Boolean) { launchAction { settingsRepository.setShowPriceStats(value) } }

    fun setDefaultCurrency(code: String) {
        launchAction { settingsRepository.setDefaultCurrency(code) }
    }

    fun exportBackup(uri: Uri) = backupAction(message(MessageKey.BACKUP_PROGRESS_EXPORT)) {
        backupFiles.export(uri)
        _backupState.update { it.copy(feedback = message(MessageKey.BACKUP_FEEDBACK_SAVED)) }
    }

    fun readBackup(uri: Uri) = backupAction(message(MessageKey.BACKUP_PROGRESS_CHECK)) {
        val backup = backupFiles.read(uri)
        _backupState.update { it.copy(pendingImport = backup, replaceConfirmation = false, restoreCurrency = true, restoreShops = true) }
    }

    fun setRestoreCurrency(value: Boolean) {
        if (!_backupState.value.busy) _backupState.update { it.copy(restoreCurrency = value) }
    }

    fun setRestoreShops(value: Boolean) {
        if (!_backupState.value.busy) _backupState.update { it.copy(restoreShops = value) }
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
        backupAction(message(MessageKey.BACKUP_PROGRESS_RESTORE)) {
            val result = backupRepository.restore(backup, replace, state.restoreCurrency, state.restoreShops)
            val currencyFeedback = if (state.restoreCurrency && !result.currencyRestored) {
                message(MessageKey.BACKUP_FEEDBACK_CURRENCY_FAILED)
            } else if (state.restoreCurrency) {
                message(MessageKey.BACKUP_FEEDBACK_CURRENCY_RESTORED, backup.defaultCurrency)
            } else {
                message(MessageKey.BACKUP_FEEDBACK_CURRENCY_UNCHANGED)
            }
            val feedback = if (backup.shopChannels == null) currencyFeedback else message(
                MessageKey.BACKUP_FEEDBACK_RESULT, currencyFeedback,
                message(when {
                    !state.restoreShops -> MessageKey.BACKUP_FEEDBACK_SHOPS_UNCHANGED
                    result.shopsRestored -> MessageKey.BACKUP_FEEDBACK_SHOPS_RESTORED
                    else -> MessageKey.BACKUP_FEEDBACK_SHOPS_FAILED
                }),
            )
            val completeFeedback = message(MessageKey.BACKUP_FEEDBACK_RESULT, feedback,
                message(if (result.priceDisplayRestored) MessageKey.BACKUP_FEEDBACK_PRICE_DISPLAY_RESTORED else MessageKey.BACKUP_FEEDBACK_PRICE_DISPLAY_FAILED))
            _backupState.update { it.copy(pendingImport = null, replaceConfirmation = false, feedback = completeFeedback) }
        }
    }

    private fun backupAction(progress: Message, block: suspend () -> Unit) {
        if (_backupState.value.busy) return
        _backupState.update { it.copy(busy = true, progress = progress, feedback = null) }
        launchAction {
            try {
                block()
            } finally {
                _backupState.update { it.copy(busy = false, progress = Message.Literal("")) }
            }
        }
    }
}
