package com.vnventory.app.ui.orders

import com.vnventory.app.ui.ActionViewModel
import kotlinx.coroutines.flow.catch
import androidx.lifecycle.viewModelScope
import com.vnventory.app.di.AppContainer
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OrderSummary
import com.vnventory.app.domain.model.PurchaseOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class OrderFormState(
    val title: String = "",
    val merchant: String = "",
    val date: LocalDate? = null,
    val currency: String = "CNY",
    val notes: String = "",
) {
    val canSave: Boolean get() = title.isNotBlank()
}

data class OrdersUiState(
    val loading: Boolean = true,
    val orders: List<OrderSummary> = emptyList(),
    val createOpen: Boolean = false,
    val creating: Boolean = false,
    val form: OrderFormState = OrderFormState(),
    val shopChannels: List<String> = emptyList(),
)

class OrdersViewModel(container: AppContainer) : ActionViewModel() {

    private val purchaseRepository = container.purchaseRepository
    private val settingsRepository = container.settingsRepository

    private val createOpen = MutableStateFlow(false)
    private val creating = MutableStateFlow(false)
    private val formState = MutableStateFlow(OrderFormState())

    val uiState: StateFlow<OrdersUiState> = combine(
        purchaseRepository.observeOrders(),
        createOpen,
        creating,
        formState,
        settingsRepository.shopChannels.catch { reportError(it); emit(emptyList()) },
    ) { orders, open, isCreating, form, shops ->
        OrdersUiState(
            loading = false,
            orders = orders,
            createOpen = open,
            creating = isCreating,
            form = form,
            shopChannels = shops,
        )
    }.catch { reportError(it); emit(OrdersUiState(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrdersUiState())

    init {
        launchAction {
            val currency = settingsRepository.defaultCurrency.first()
            formState.update { it.copy(currency = currency) }
        }
    }

    fun openCreate() {
        clearError()
        createOpen.value = true
    }

    fun closeCreate() {
        createOpen.value = false
    }

    fun onTitleChange(value: String) = formState.update { it.copy(title = value) }
    fun onMerchantChange(value: String) = formState.update { it.copy(merchant = value) }
    fun onDateChange(date: LocalDate?) = formState.update { it.copy(date = date) }
    fun onCurrencyChange(code: String) = formState.update { it.copy(currency = Money.normalize(code)) }
    fun onNotesChange(value: String) = formState.update { it.copy(notes = value) }

    fun createOrder(onCreated: (Long) -> Unit) {
        val form = formState.value
        if (!form.canSave || creating.value) return
        launchAction {
            creating.value = true
            try {
                val now = System.currentTimeMillis()
                val id = purchaseRepository.createOrder(
                    PurchaseOrder(
                        id = 0,
                        title = form.title.trim(),
                        merchant = form.merchant.trim().takeIf { it.isNotBlank() },
                        orderDate = form.date,
                        currency = Money.normalize(form.currency),
                        notes = form.notes.takeIf { it.isNotBlank() },
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                formState.value = OrderFormState(currency = form.currency)
                createOpen.value = false
                onCreated(id)
            } finally {
                creating.value = false
            }
        }
    }

    fun deleteOrder(orderId: Long) {
        launchAction { purchaseRepository.deleteOrder(orderId) }
    }
}
