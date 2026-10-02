package com.vnventory.app.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnventory.app.core.AppResult
import com.vnventory.app.di.AppContainer
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OrderSummary
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.domain.model.ReleaseInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EditFormState(
    val releaseTitle: String = "",
    val priceText: String = "",
    val currency: String = "CNY",
    val condition: CopyCondition = CopyCondition.USED,
    val conditionNote: String = "",
    val purchaseDate: LocalDate? = null,
    val shop: String = "",
    val notes: String = "",
    val orderId: Long? = null,
) {
    val parsedPrice: Long? get() = Money.parse(priceText, currency)
    val priceValid: Boolean get() = priceText.isBlank() || parsedPrice != null
    val conditionValid: Boolean get() = condition != CopyCondition.CUSTOM || conditionNote.isNotBlank()
    val canSave: Boolean get() = priceValid && conditionValid
}

data class BindSheetState(
    val open: Boolean = false,
    val loading: Boolean = false,
    val releases: List<ReleaseInfo> = emptyList(),
    val error: String? = null,
)

data class CopyEditUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val copy: OwnedCopy? = null,
    val form: EditFormState = EditFormState(),
    val orders: List<OrderSummary> = emptyList(),
    val saving: Boolean = false,
    val bindSheet: BindSheetState = BindSheetState(),
)

class CopyEditViewModel(
    container: AppContainer,
    private val copyId: Long,
) : ViewModel() {

    private val collectionRepository = container.collectionRepository
    private val vnRepository = container.vnRepository
    private val purchaseRepository = container.purchaseRepository

    private val copyState = MutableStateFlow<OwnedCopy?>(null)
    private val loadedState = MutableStateFlow(false)
    private val formState = MutableStateFlow(EditFormState())
    private val savingState = MutableStateFlow(false)
    private val bindSheetState = MutableStateFlow(BindSheetState())

    val uiState: StateFlow<CopyEditUiState> = combine(
        copyState,
        loadedState,
        formState,
        combine(savingState, bindSheetState) { saving, bind -> saving to bind },
        purchaseRepository.observeOrders(),
    ) { copy, loaded, form, (saving, bind), orders ->
        CopyEditUiState(
            loading = !loaded,
            notFound = loaded && copy == null,
            copy = copy,
            form = form,
            orders = orders,
            saving = saving,
            bindSheet = bind,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CopyEditUiState())

    init {
        viewModelScope.launch {
            val copy = collectionRepository.getById(copyId)
            copyState.value = copy
            if (copy != null) {
                formState.value = EditFormState(
                    releaseTitle = copy.releaseTitle.orEmpty(),
                    priceText = Money.toEditableString(copy.priceMinor, copy.currency),
                    currency = copy.currency,
                    condition = copy.condition,
                    conditionNote = copy.conditionNote.orEmpty(),
                    purchaseDate = copy.purchaseDate,
                    shop = copy.shop.orEmpty(),
                    notes = copy.notes.orEmpty(),
                    orderId = copy.orderId,
                )
            }
            loadedState.value = true
        }
    }

    // ---- 表单 ----

    fun onReleaseTitleChange(value: String) = formState.update { it.copy(releaseTitle = value) }
    fun onPriceChange(value: String) = formState.update { it.copy(priceText = value) }
    fun onCurrencyChange(code: String) = formState.update { it.copy(currency = Money.normalize(code)) }
    fun onConditionChange(condition: CopyCondition) = formState.update { it.copy(condition = condition) }
    fun onConditionNoteChange(value: String) = formState.update { it.copy(conditionNote = value) }
    fun onPurchaseDateChange(date: LocalDate?) = formState.update { it.copy(purchaseDate = date) }
    fun onShopChange(value: String) = formState.update { it.copy(shop = value) }
    fun onNotesChange(value: String) = formState.update { it.copy(notes = value) }
    fun onOrderChange(orderId: Long?) = formState.update { it.copy(orderId = orderId) }

    // ---- 保存 / 删除 ----

    fun save(onSaved: () -> Unit) {
        val copy = copyState.value ?: return
        val form = formState.value
        if (!form.canSave || savingState.value) return

        viewModelScope.launch {
            savingState.value = true
            try {
                collectionRepository.update(
                    copy.copy(
                        releaseTitle = if (copy.releaseId == null) {
                            form.releaseTitle.takeIf { it.isNotBlank() }
                        } else {
                            copy.releaseTitle
                        },
                        priceMinor = form.parsedPrice ?: 0L,
                        currency = Money.normalize(form.currency),
                        condition = form.condition,
                        conditionNote = form.conditionNote.takeIf { it.isNotBlank() },
                        purchaseDate = form.purchaseDate,
                        shop = form.shop.takeIf { it.isNotBlank() },
                        notes = form.notes.takeIf { it.isNotBlank() },
                        orderId = form.orderId,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } finally {
                savingState.value = false
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            collectionRepository.delete(copyId)
            onDeleted()
        }
    }

    // ---- 手动版本绑定 VNDB Release ----

    fun openBindSheet() {
        val copy = copyState.value ?: return
        bindSheetState.value = BindSheetState(open = true, loading = true)
        viewModelScope.launch {
            val cached = vnRepository.observeCachedReleases(copy.vnId).first()
            if (cached.isNotEmpty()) {
                bindSheetState.update { it.copy(releases = cached) }
            }
            when (val result = vnRepository.fetchReleases(copy.vnId)) {
                is AppResult.Success -> bindSheetState.update {
                    it.copy(releases = result.data, loading = false, error = null)
                }

                is AppResult.Failure -> bindSheetState.update {
                    it.copy(
                        loading = false,
                        error = if (it.releases.isEmpty()) result.error.message else null,
                    )
                }
            }
        }
    }

    fun closeBindSheet() = bindSheetState.update { it.copy(open = false) }

    fun bindTo(release: ReleaseInfo) {
        val copy = copyState.value ?: return
        viewModelScope.launch {
            collectionRepository.bindRelease(copy.id, release, coverUrl = release.displayImage())
            copyState.value = collectionRepository.getById(copy.id)
            formState.update { it.copy(releaseTitle = release.title) }
            bindSheetState.value = BindSheetState()
        }
    }
}
