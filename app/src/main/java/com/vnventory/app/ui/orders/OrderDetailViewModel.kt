package com.vnventory.app.ui.orders

import com.vnventory.app.di.AppContainer
import com.vnventory.app.data.repository.PurchaseRepository
import com.vnventory.app.domain.cost.CostEngine
import com.vnventory.app.domain.cost.OrderCostBreakdown
import com.vnventory.app.domain.model.*
import com.vnventory.app.ui.ActionViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*

data class ExpenseEditorState(
    val open: Boolean = false,
    val editingId: Long? = null,
    val name: String = "",
    val category: ExpenseCategory = ExpenseCategory.SHIPPING,
    val amountText: String = "",
    val currency: String = "CNY",
    val mode: AllocationMode = AllocationMode.EQUAL,
    val manualInputs: Map<Long, String> = emptyMap(),
) {
    val parsedAmount: Long? get() = Money.parse(amountText, currency)
    // Blank allocations explicitly mean zero; a non-blank malformed input is never discarded.
    val parsedManual: Map<Long, Long>?
        get() {
            val result = mutableMapOf<Long, Long>()
            manualInputs.forEach { (id, text) -> result[id] = if (text.isBlank()) 0L else Money.parse(text, currency) ?: return null }
            return result
        }
    val inputError: String?
        get() {
            if (name.isBlank()) return "请填写费用名称"
            val amount = parsedAmount ?: return "请填写有效的非负金额"
            if (mode == AllocationMode.MANUAL) {
                val allocations = parsedManual ?: return "手动分摊中有无效金额，请逐项修正"
                val sum = try { Money.sum(allocations.values) } catch (_: IllegalArgumentException) { return "分摊合计超出可支持的范围" }
                if (sum > amount) return "手动分摊合计不能超过费用总额"
            }
            return null
        }
    val canSave: Boolean get() = inputError == null

    fun candidate(detail: OrderDetail): Expense {
        require(inputError == null) { inputError!! }
        val old = editingId?.let { id -> requireNotNull(detail.expenses.find { it.id == id }) { "费用已不存在" } }
        return Expense(editingId ?: 0, detail.order.id, name.trim(), category, parsedAmount!!,
            Money.normalize(currency), mode, old?.notes, old?.createdAt ?: System.currentTimeMillis(),
            if (mode == AllocationMode.MANUAL) parsedManual!! else emptyMap())
    }

    fun validationError(detail: OrderDetail): String? {
        inputError?.let { return it }
        return try {
            CostEngine.expenseProblem(candidate(detail).costInput(), detail.copies.map { it.costInput() })
        } catch (e: IllegalArgumentException) { e.message }
    }
}

data class OrderDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val detail: OrderDetail? = null,
    val editor: ExpenseEditorState = ExpenseEditorState(),
    val savingExpense: Boolean = false,
    val editorError: String? = null,
    val preview: OrderCostBreakdown? = null,
)

class OrderDetailViewModel(
    private val purchaseRepository: PurchaseRepository,
    private val orderId: Long,
) : ActionViewModel() {
    constructor(container: AppContainer, orderId: Long) : this(container.purchaseRepository, orderId)
    private val editorState = MutableStateFlow(ExpenseEditorState())
    private val savingState = MutableStateFlow(false)
    private val loaded = MutableStateFlow(false)
    private val detailState = MutableStateFlow<OrderDetail?>(null)

    val uiState: StateFlow<OrderDetailUiState> = combine(detailState, editorState, savingState, loaded) { detail, editor, saving, ready ->
        var error = if (editor.open && detail != null) editor.validationError(detail) else null
        val preview = if (editor.open && detail != null && error == null) {
            try { detail.previewExpense(editor.candidate(detail)) } catch (e: IllegalArgumentException) { error = e.message; null }
        } else null
        OrderDetailUiState(!ready, ready && detail == null, detail, editor, saving, error, preview)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderDetailUiState())

    init {
        launchAction {
            try { purchaseRepository.observeOrderDetail(orderId).collect { detailState.value = it; loaded.value = true } }
            finally { loaded.value = true }
        }
    }

    fun openNewExpense() { clearError(); editorState.value = ExpenseEditorState(open = true, currency = detailState.value?.order?.currency ?: "CNY") }
    fun openEditExpense(expense: Expense) {
        clearError()
        val currentCopyIds = detailState.value?.copies.orEmpty().map { it.id }.toSet()
        editorState.value = ExpenseEditorState(true, expense.id, expense.name, expense.category,
            Money.toEditableString(expense.amountMinor, expense.currency), expense.currency, expense.mode,
            expense.allocations.filterKeys { it in currentCopyIds }
                .mapValues { Money.toEditableString(it.value, expense.currency) })
    }
    fun closeExpenseEditor() { if (!savingState.value) { clearError(); editorState.update { it.copy(open = false) } } }
    fun onExpenseNameChange(value: String) = editorState.update { it.copy(name = value) }
    fun onExpenseCategoryChange(value: ExpenseCategory) = editorState.update { it.copy(category = value) }
    fun onExpenseAmountChange(value: String) = editorState.update { it.copy(amountText = value) }
    fun onExpenseCurrencyChange(value: String) = editorState.update { it.copy(currency = Money.normalize(value)) }
    fun onExpenseModeChange(mode: AllocationMode) {
        val detail = detailState.value
        editorState.update { editor ->
            val inputs = if (mode == AllocationMode.MANUAL && editor.manualInputs.isEmpty() && detail != null) {
                val amounts = CostEngine.equalSplitPreview(editor.parsedAmount ?: 0, detail.copies.size)
                detail.copies.mapIndexed { index, copy -> copy.id to Money.toEditableString(amounts[index], editor.currency) }.toMap()
            } else editor.manualInputs
            editor.copy(mode = mode, manualInputs = inputs)
        }
    }
    fun onManualAmountChange(copyId: Long, text: String) = editorState.update { it.copy(manualInputs = it.manualInputs + (copyId to text)) }

    fun saveExpense() {
        val detail = detailState.value ?: return
        val editor = editorState.value
        if (savingState.value) return
        val error = editor.validationError(detail)
        if (error != null) { reportError(IllegalArgumentException(error)); return }
        savingState.value = true
        launchAction {
            try {
                val candidate = editor.candidate(detail)
                if (editor.editingId == null) purchaseRepository.addExpense(candidate) else purchaseRepository.updateExpense(candidate)
                editorState.value = ExpenseEditorState()
            } finally { savingState.value = false }
        }
    }
    fun deleteExpense(expenseId: Long) { launchAction { purchaseRepository.deleteExpense(expenseId) } }
    fun removeCopyFromOrder(copyId: Long) { launchAction { purchaseRepository.assignCopiesToOrder(listOf(copyId), null) } }
    fun deleteOrder(onDeleted: () -> Unit) { launchAction { purchaseRepository.deleteOrder(orderId); onDeleted() } }
}
