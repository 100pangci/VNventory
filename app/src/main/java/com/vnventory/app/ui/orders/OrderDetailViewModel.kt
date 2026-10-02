package com.vnventory.app.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnventory.app.core.AppResult
import com.vnventory.app.di.AppContainer
import com.vnventory.app.domain.cost.CostEngine
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.Expense
import com.vnventory.app.domain.model.ExpenseCategory
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OrderDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpenseEditorState(
    val open: Boolean = false,
    val editingId: Long? = null,
    val name: String = "",
    val category: ExpenseCategory = ExpenseCategory.SHIPPING,
    val amountText: String = "",
    val currency: String = "CNY",
    val mode: AllocationMode = AllocationMode.EQUAL,
    /** 手动分摊输入：copyId -> 文本 */
    val manualInputs: Map<Long, String> = emptyMap(),
) {
    val parsedAmount: Long? get() = Money.parse(amountText, currency)
    val canSave: Boolean
        get() = name.isNotBlank() && parsedAmount != null &&
            (mode != AllocationMode.MANUAL || manualInputs.values.any { it.isNotBlank() })
}

data class OrderDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val detail: OrderDetail? = null,
    val editor: ExpenseEditorState = ExpenseEditorState(),
    val savingExpense: Boolean = false,
    val error: String? = null,
)

class OrderDetailViewModel(
    container: AppContainer,
    private val orderId: Long,
) : ViewModel() {

    private val purchaseRepository = container.purchaseRepository

    private val editorState = MutableStateFlow(ExpenseEditorState())
    private val savingState = MutableStateFlow(false)
    private val errorState = MutableStateFlow<String?>(null)

    /** 需要一并发给编辑器的“订单默认货币”（由详情流填充） */
    private val orderCurrency = MutableStateFlow("CNY")
    private val loaded = MutableStateFlow(false)

    private val detailFlow = purchaseRepository.observeOrderDetail(orderId)

    val uiState: StateFlow<OrderDetailUiState> = combine(
        detailFlow,
        combine(editorState, savingState) { editor, saving -> editor to saving },
        combine(errorState, loaded) { error, isLoaded -> error to isLoaded },
    ) { detail, (editor, saving), (error, isLoaded) ->
        OrderDetailUiState(
            loading = !isLoaded,
            notFound = isLoaded && detail == null,
            detail = detail,
            editor = editor,
            savingExpense = saving,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderDetailUiState())

    init {
        viewModelScope.launch {
            detailFlow.collect { detail ->
                orderCurrency.value = detail?.order?.currency ?: "CNY"
                loaded.value = true
            }
        }
    }

    // ---- 费用编辑器 ----

    fun openNewExpense() {
        editorState.value = ExpenseEditorState(
            open = true,
            currency = orderCurrency.value,
        )
    }

    fun openEditExpense(expense: Expense) {
        editorState.value = ExpenseEditorState(
            open = true,
            editingId = expense.id,
            name = expense.name,
            category = expense.category,
            amountText = Money.toEditableString(expense.amountMinor, expense.currency),
            currency = expense.currency,
            mode = expense.mode,
            manualInputs = expense.allocations.mapValues { (_, amount) ->
                Money.toEditableString(amount, expense.currency)
            },
        )
    }

    fun closeExpenseEditor() {
        editorState.update { it.copy(open = false) }
    }

    fun onExpenseNameChange(value: String) = editorState.update { it.copy(name = value) }
    fun onExpenseCategoryChange(value: ExpenseCategory) = editorState.update { it.copy(category = value) }
    fun onExpenseAmountChange(value: String) = editorState.update { it.copy(amountText = value) }
    fun onExpenseCurrencyChange(value: String) = editorState.update { it.copy(currency = Money.normalize(value)) }

    /** 切换分摊方式；切到 MANUAL 时用“平均分摊预览”预填每盒金额 */
    fun onExpenseModeChange(mode: AllocationMode) {
        val detail = uiState.value.detail
        editorState.update { editor ->
            val prefilled = if (mode == AllocationMode.MANUAL && editor.manualInputs.isEmpty() && detail != null) {
                val amount = editor.parsedAmount ?: 0L
                val splits = CostEngine.equalSplitPreview(amount, detail.copies.size)
                detail.copies.mapIndexed { index, copy ->
                    copy.id to Money.toEditableString(splits[index], editor.currency)
                }.toMap()
            } else {
                editor.manualInputs
            }
            editor.copy(mode = mode, manualInputs = prefilled)
        }
    }

    fun onManualAmountChange(copyId: Long, text: String) {
        editorState.update { it.copy(manualInputs = it.manualInputs + (copyId to text)) }
    }

    fun saveExpense() {
        val detail = uiState.value.detail ?: return
        val editor = editorState.value
        if (!editor.canSave || savingState.value) return

        viewModelScope.launch {
            savingState.value = true
            errorState.value = null
            try {
                val amount = editor.parsedAmount ?: 0L
                val currency = Money.normalize(editor.currency)
                val existing = editor.editingId?.let { id -> detail.expenses.firstOrNull { it.id == id } }
                val allocations = if (editor.mode == AllocationMode.MANUAL) {
                    editor.manualInputs.mapNotNull { (copyId, text) ->
                        Money.parse(text, currency)?.let { copyId to it }
                    }.toMap()
                } else {
                    emptyMap()
                }
                val expense = Expense(
                    id = editor.editingId ?: 0,
                    orderId = detail.order.id,
                    name = editor.name.trim(),
                    category = editor.category,
                    amountMinor = amount,
                    currency = currency,
                    mode = editor.mode,
                    notes = existing?.notes,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    allocations = allocations,
                )
                if (editor.editingId == null) {
                    purchaseRepository.addExpense(expense)
                } else {
                    purchaseRepository.updateExpense(expense)
                }
                editorState.value = ExpenseEditorState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                errorState.value = e.message ?: "保存费用失败"
            } finally {
                savingState.value = false
            }
        }
    }

    fun deleteExpense(expenseId: Long) {
        viewModelScope.launch { purchaseRepository.deleteExpense(expenseId) }
    }

    // ---- 订单操作 ----

    fun removeCopyFromOrder(copyId: Long) {
        viewModelScope.launch { purchaseRepository.assignCopiesToOrder(listOf(copyId), orderId = null) }
    }

    fun deleteOrder(onDeleted: () -> Unit) {
        viewModelScope.launch {
            purchaseRepository.deleteOrder(orderId)
            onDeleted()
        }
    }
}
