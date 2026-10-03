package com.vnventory.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnventory.app.core.toAppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 写入失败保留表单，显示可关闭错误；协程取消始终继续传播。 */
abstract class ActionViewModel : ViewModel() {
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()

    fun clearError() {
        _actionError.value = null
    }

    protected fun reportError(error: Throwable) {
        if (error is CancellationException) throw error
        _actionError.value = error.toAppError().message
    }
    protected fun launchAction(block: suspend () -> Unit) = viewModelScope.launch {
        clearError()
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportError(e)
        }
    }
}
