package com.vnventory.app.ui.orders

import com.vnventory.app.di.AppContainer
import com.vnventory.app.data.repository.PurchaseRepository
import com.vnventory.app.data.repository.SettingsRepository
import com.vnventory.app.domain.cost.CostEngine
import com.vnventory.app.domain.cost.OrderCostBreakdown
import com.vnventory.app.domain.model.*
import com.vnventory.app.ui.ActionViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import com.vnventory.app.core.toAppError
import com.vnventory.app.domain.text.Message
import com.vnventory.app.domain.text.MessageException
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import com.vnventory.app.domain.text.requireMessage
import com.vnventory.app.domain.text.requireNotNullMessage

data class ExpenseEditorState(
    val open: Boolean = false,
    val editingId: Long? = null,
    val name: String = "",
    val category: ExpenseCategory = ExpenseCategory.INTERNATIONAL_SHIPPING,
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
    val inputError: Message?
        get() {
            if (editingId == null && !category.isFixed) return message(MessageKey.INPUT_EXPENSE_CATEGORY)
            val amount = parsedAmount ?: return message(MessageKey.INPUT_NONNEGATIVE_AMOUNT)
            if (mode == AllocationMode.MANUAL) {
                val allocations = parsedManual ?: return message(MessageKey.INPUT_MANUAL_INVALID)
                val sum = try { Money.sum(allocations.values) } catch (_: IllegalArgumentException) { return message(MessageKey.ALLOCATION_TOTAL_OVERFLOW) }
                if (sum > amount) return message(MessageKey.ALLOCATION_EXCESS)
            }
            return null
        }
    val canSave: Boolean get() = inputError == null

    fun candidate(detail: OrderDetail): Expense {
        requireMessage(inputError == null) { inputError!! }
        val old = editingId?.let { id -> requireNotNullMessage(detail.expenses.find { it.id == id }) { message(MessageKey.EXPENSE_MISSING) } }
        val savedName = if (old != null && old.category == category) old.name else category.name
        return Expense(editingId ?: 0, detail.order.id, savedName, category, parsedAmount!!,
            Money.normalize(currency), mode, old?.notes, old?.createdAt ?: System.currentTimeMillis(),
            if (mode == AllocationMode.MANUAL) parsedManual!! else emptyMap())
    }

    fun validationError(detail: OrderDetail): Message? {
        inputError?.let { return it }
        return try {
            CostEngine.expenseProblem(candidate(detail).costInput(), detail.copies.map { it.costInput() })
        } catch (e: IllegalArgumentException) { e.toAppError().message }
    }
}

data class OrderDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val detail: OrderDetail? = null,
    val editor: ExpenseEditorState = ExpenseEditorState(),
    val savingExpense: Boolean = false,
    val editorError: Message? = null,
    val preview: OrderCostBreakdown? = null,
    val orderEditor: OrderEditorState = OrderEditorState(),
    val shopChannels: List<String> = emptyList(),
)

data class OrderEditorState(
    val open: Boolean = false,
    val saving: Boolean = false,
    val form: OrderFormState = OrderFormState(),
)

class OrderDetailViewModel(
    private val purchaseRepository: PurchaseRepository,
    private val orderId: Long,
    private val settingsRepository: SettingsRepository? = null,
) : ActionViewModel() {
    constructor(container: AppContainer, orderId: Long) : this(container.purchaseRepository, orderId, container.settingsRepository)
    private val editorState = MutableStateFlow(ExpenseEditorState())
    private val savingState = MutableStateFlow(false)
    private val loaded = MutableStateFlow(false)
    private val detailState = MutableStateFlow<OrderDetail?>(null)
    private val orderEditorState = MutableStateFlow(OrderEditorState())

    private val expenseUiState = combine(detailState, editorState, savingState, loaded) { detail, editor, saving, ready ->
        var error = if (editor.open && detail != null) editor.validationError(detail) else null
        val preview = if (editor.open && detail != null && error == null) {
            try { detail.previewExpense(editor.candidate(detail)) } catch (e: IllegalArgumentException) { error = e.toAppError().message; null }
        } else null
        OrderDetailUiState(!ready, ready && detail == null, detail, editor, saving, error, preview)
    }
    val uiState: StateFlow<OrderDetailUiState> = combine(expenseUiState, orderEditorState,
        settingsRepository?.shopChannels?.catch { reportError(it); emit(emptyList()) } ?: flowOf(emptyList())) { state, editor, shops ->
        state.copy(orderEditor = editor, shopChannels = shops)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderDetailUiState())

    init {
        launchAction {
            try { purchaseRepository.observeOrderDetail(orderId).collect { detailState.value = it; loaded.value = true } }
            finally { loaded.value = true }
        }
    }

    fun openOrderEditor() {
        val order = detailState.value?.order ?: return
        if (savingState.value || editorState.value.open || orderEditorState.value.saving) return
        clearError()
        orderEditorState.value = OrderEditorState(open = true, form = OrderFormState(
            order.title, order.merchant.orEmpty(), order.orderDate, order.currency, order.notes.orEmpty()))
    }
    fun closeOrderEditor() {
        if (!orderEditorState.value.saving) { clearError(); orderEditorState.value = OrderEditorState() }
    }
    private fun updateOrderForm(change: (OrderFormState) -> OrderFormState) {
        orderEditorState.update { if (it.saving) it else it.copy(form = change(it.form)) }
    }
    fun onOrderTitleChange(value: String) = updateOrderForm { it.copy(title = value) }
    fun onOrderMerchantChange(value: String) = updateOrderForm { it.copy(merchant = value) }
    fun onOrderDateChange(value: java.time.LocalDate?) = updateOrderForm { it.copy(date = value) }
    fun onOrderCurrencyChange(value: String) = updateOrderForm { it.copy(currency = Money.normalize(value)) }
    fun onOrderNotesChange(value: String) = updateOrderForm { it.copy(notes = value) }
    fun saveOrder() {
        val editor = orderEditorState.value
        if (!editor.open || editor.saving || !editor.form.canSave) return
        orderEditorState.update { it.copy(saving = true) }
        launchAction {
            try {
                val old = requireNotNullMessage(purchaseRepository.getOrder(orderId)) { message(MessageKey.ORDER_MISSING) }
                val form = editor.form
                purchaseRepository.updateOrder(old.copy(title = form.title.trim(),
                    merchant = form.merchant.trim().takeIf { it.isNotBlank() }, orderDate = form.date,
                    currency = Money.normalize(form.currency), notes = form.notes.takeIf { it.isNotBlank() },
                    updatedAt = System.currentTimeMillis()))
                orderEditorState.value = OrderEditorState()
            } finally { orderEditorState.update { it.copy(saving = false) } }
        }
    }

    fun openNewExpense() {
        if (orderEditorState.value.open) return
        clearError(); editorState.value = ExpenseEditorState(open = true, currency = detailState.value?.order?.currency ?: "CNY")
    }
    fun openEditExpense(expense: Expense) {
        clearError()
        val currentCopyIds = detailState.value?.copies.orEmpty().map { it.id }.toSet()
        editorState.value = ExpenseEditorState(true, expense.id, expense.name, expense.category,
            Money.toEditableString(expense.amountMinor, expense.currency), expense.currency, expense.mode,
            expense.allocations.filterKeys { it in currentCopyIds }
                .mapValues { Money.toEditableString(it.value, expense.currency) })
    }
    fun closeExpenseEditor() { if (!savingState.value) { clearError(); editorState.update { it.copy(open = false) } } }
    fun onExpenseCategoryChange(value: ExpenseCategory) {
        if (value.isFixed) editorState.update { it.copy(category = value) }
    }
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
        if (error != null) { reportError(MessageException(error)); return }
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
