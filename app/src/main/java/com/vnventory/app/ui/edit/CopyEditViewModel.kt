package com.vnventory.app.ui.edit

import com.vnventory.app.ui.ActionViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import androidx.lifecycle.viewModelScope
import com.vnventory.app.core.AppResult
import com.vnventory.app.di.AppContainer
import com.vnventory.app.data.repository.CollectionRepository
import com.vnventory.app.data.repository.PurchaseRepository
import com.vnventory.app.data.repository.VnRepository
import com.vnventory.app.data.repository.SettingsRepository
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
import com.vnventory.app.domain.text.Message
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import com.vnventory.app.domain.text.requireMessage

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
    val canSave: Boolean get() = priceValid && conditionValid && releaseTitle.isNotBlank()
}

data class BindSheetState(
    val open: Boolean = false,
    val loading: Boolean = false,
    val releases: List<ReleaseInfo> = emptyList(),
    val error: Message? = null,
)

data class CopyEditUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val copy: OwnedCopy? = null,
    val form: EditFormState = EditFormState(),
    val orders: List<OrderSummary> = emptyList(),
    val saving: Boolean = false,
    val bindSheet: BindSheetState = BindSheetState(),
    val shopChannels: List<String> = emptyList(),
)

class CopyEditViewModel(
    private val collectionRepository: CollectionRepository,
    private val vnRepository: VnRepository,
    private val purchaseRepository: PurchaseRepository,
    private val copyId: Long,
    private val settingsRepository: SettingsRepository,
) : ActionViewModel() {
    constructor(container: AppContainer, copyId: Long) : this(
        container.collectionRepository, container.vnRepository, container.purchaseRepository, copyId, container.settingsRepository,
    )
    private var bindJob: Job? = null
    private var bindGeneration = 0

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
        combine(purchaseRepository.observeOrders(), settingsRepository.shopChannels.catch { reportError(it); emit(emptyList()) }) { orders, shops -> orders to shops },
    ) { copy, loaded, form, (saving, bind), (orders, shops) ->
        CopyEditUiState(
            loading = !loaded,
            notFound = loaded && copy == null,
            copy = copy,
            form = form,
            orders = orders,
            saving = saving,
            bindSheet = bind,
            shopChannels = shops,
        )
    }.catch { reportError(it); emit(CopyEditUiState(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CopyEditUiState())

    init {
        launchAction {
            try {
            val copy = collectionRepository.getById(copyId)
            copyState.value = copy
            if (copy != null) {
                formState.value = EditFormState(
                    releaseTitle = copy.releaseTitle.orEmpty(),
                    priceText = copy.priceMinor?.let { Money.toEditableString(it, copy.currency) }.orEmpty(),
                    currency = copy.currency,
                    condition = copy.condition,
                    conditionNote = copy.conditionNote.orEmpty(),
                    purchaseDate = copy.purchaseDate,
                    shop = copy.shop.orEmpty(),
                    notes = copy.notes.orEmpty(),
                    orderId = copy.orderId,
                )
            }
            } finally { loadedState.value = true }
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

        launchAction {
            savingState.value = true
            try {
                collectionRepository.update(
                    copy.copy(
                        releaseTitle = if (copy.releaseId == null) {
                            form.releaseTitle.takeIf { it.isNotBlank() }
                        } else {
                            copy.releaseTitle
                        },
                        priceMinor = form.parsedPrice,
                        currency = Money.normalize(form.currency),
                        condition = form.condition,
                        conditionNote = form.conditionNote.takeIf { it.isNotBlank() },
                        purchaseDate = form.purchaseDate,
                        shop = form.shop.trim().takeIf { it.isNotBlank() },
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
        launchAction {
            collectionRepository.delete(copyId)
            onDeleted()
        }
    }

    // ---- 手动版本绑定 VNDB Release ----

    fun openBindSheet() {
        val copy = copyState.value ?: return
        bindJob?.cancel()
        val generation = ++bindGeneration
        bindSheetState.value = BindSheetState(open = true, loading = true)
        bindJob = launchAction {
            try {
            val cached = vnRepository.observeCachedReleases(copy.vnId).first()
            if (generation != bindGeneration) return@launchAction
            if (cached.isNotEmpty()) {
                bindSheetState.update { it.copy(releases = cached) }
            }
            val result = vnRepository.fetchReleases(copy.vnId)
            if (generation != bindGeneration) return@launchAction
            when (result) {
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
            } finally { if (generation == bindGeneration) bindSheetState.update { it.copy(loading = false) } }
        }
    }

    fun closeBindSheet() {
        bindGeneration++
        bindJob?.cancel()
        bindSheetState.update { it.copy(open = false) }
    }

    fun bindTo(release: ReleaseInfo) {
        val copy = copyState.value ?: return
        launchAction {
            requireMessage(release.vnId == copy.vnId) { message(MessageKey.RELEASE_VN_MISMATCH) }
            collectionRepository.bindRelease(copy.id, release, coverUrl = release.displayImage())
            copyState.value = collectionRepository.getById(copy.id)
            formState.update { it.copy(releaseTitle = release.title) }
            bindSheetState.value = BindSheetState()
        }
    }
}
