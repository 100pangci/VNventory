package com.vnventory.app.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnventory.app.core.AppResult
import com.vnventory.app.di.AppContainer
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OrderSummary
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.domain.model.ReleaseInfo
import com.vnventory.app.domain.model.VnInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---------------------------------------------------------------------------
// UI 状态
// ---------------------------------------------------------------------------

data class VnSearchUiState(
    val query: String = "",
    val results: List<VnInfo> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
    val hasSearched: Boolean = false,
)

data class ReleasesUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val releases: List<ReleaseInfo> = emptyList(),
)

data class PurchaseFormState(
    val releaseId: String? = null,
    /** Release 名；手动版本时为用户输入的版本名 */
    val releaseTitle: String = "",
    val manualVersion: Boolean = false,
    val priceText: String = "",
    val currency: String = "CNY",
    val condition: CopyCondition = CopyCondition.USED,
    val conditionNote: String = "",
    val purchaseDate: LocalDate? = null,
    val shop: String = "",
    val notes: String = "",
    val quantity: Int = 1,
    val orderId: Long? = null,
) {
    val parsedPrice: Long? get() = Money.parse(priceText, currency)

    /** 空价格按 0 处理；非空则必须可解析 */
    val priceValid: Boolean get() = priceText.isBlank() || parsedPrice != null

    val quantityValid: Boolean get() = quantity in 1..99

    val manualVersionValid: Boolean get() = !manualVersion || releaseTitle.isNotBlank()

    val canSave: Boolean get() = priceValid && quantityValid && manualVersionValid
}

data class AddFlowUiState(
    /** 从订单页进入时带入的订单上下文 */
    val orderContextId: Long? = null,
    val orders: List<OrderSummary> = emptyList(),
    val search: VnSearchUiState = VnSearchUiState(),
    val selectedVn: VnInfo? = null,
    val releases: ReleasesUiState = ReleasesUiState(),
    val form: PurchaseFormState = PurchaseFormState(),
    val saving: Boolean = false,
)

sealed interface AddFlowEvent {
    data class Saved(val copyIds: List<Long>) : AddFlowEvent
    data class Failed(val message: String) : AddFlowEvent
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class AddFlowViewModel(
    container: AppContainer,
    private val orderIdArg: Long?,
) : ViewModel() {

    private val vnRepository = container.vnRepository
    private val collectionRepository = container.collectionRepository
    private val purchaseRepository = container.purchaseRepository
    private val settingsRepository = container.settingsRepository

    private val searchState = MutableStateFlow(VnSearchUiState())
    private val selectedVn = MutableStateFlow<VnInfo?>(null)
    private val releasesState = MutableStateFlow(ReleasesUiState())
    private val formState = MutableStateFlow(PurchaseFormState())
    private val savingState = MutableStateFlow(false)

    private val eventsChannel = Channel<AddFlowEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<AddFlowUiState> = combine(
        combine(searchState, selectedVn, releasesState) { search, vn, releases ->
            Triple(search, vn, releases)
        },
        combine(formState, savingState) { form, saving -> form to saving },
        purchaseRepository.observeOrders(),
    ) { (search, vn, releases), (form, saving), orders ->
        AddFlowUiState(
            orderContextId = orderIdArg,
            orders = orders,
            search = search,
            selectedVn = vn,
            releases = releases,
            form = form,
            saving = saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddFlowUiState())

    init {
        // 默认货币：订单上下文优先，否则用设置里的默认货币
        viewModelScope.launch {
            val defaultCurrency = settingsRepository.defaultCurrency.first()
            val orderCurrency = orderIdArg?.let { purchaseRepository.getOrder(it)?.currency }
            formState.update {
                it.copy(currency = orderCurrency ?: defaultCurrency, orderId = orderIdArg)
            }
        }
        // 搜索防抖
        viewModelScope.launch {
            searchState
                .map { it.query }
                .debounce(350)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isBlank()) {
                        searchState.update { VnSearchUiState() }
                    } else {
                        performSearch(query)
                    }
                }
        }
    }

    // ---- 搜索 ----

    fun onQueryChange(text: String) {
        searchState.update { it.copy(query = text) }
    }

    private suspend fun performSearch(query: String) {
        searchState.update { it.copy(loading = true, error = null, hasSearched = true) }
        when (val result = vnRepository.searchVn(query)) {
            is AppResult.Success -> searchState.update {
                it.copy(
                    results = result.data.items,
                    offline = result.data.offline,
                    loading = false,
                )
            }

            is AppResult.Failure -> searchState.update {
                it.copy(loading = false, error = result.error.message)
            }
        }
    }

    // ---- 选 VN / Release ----

    fun selectVn(vn: VnInfo) {
        selectedVn.value = vn
        formState.update { it.copy(releaseId = null, releaseTitle = "", manualVersion = false) }
        viewModelScope.launch {
            releasesState.value = ReleasesUiState(loading = true)
            // 先展示本地缓存
            val cached = vnRepository.observeCachedReleases(vn.id).first()
            if (cached.isNotEmpty()) {
                releasesState.update { it.copy(releases = cached) }
            }
            when (val result = vnRepository.fetchReleases(vn.id)) {
                is AppResult.Success -> releasesState.update {
                    it.copy(releases = result.data, loading = false, error = null)
                }

                is AppResult.Failure -> releasesState.update {
                    it.copy(loading = false, error = result.error.message)
                }
            }
        }
    }

    fun backToSearch() {
        selectedVn.value = null
        releasesState.value = ReleasesUiState()
    }

    /** 从‘购入信息’返回‘选择版本’ */
    fun backToReleases() {
        formState.update { it.copy(releaseId = null, manualVersion = false, releaseTitle = "") }
    }

    fun selectRelease(release: ReleaseInfo) {
        formState.update {
            it.copy(releaseId = release.id, releaseTitle = release.title, manualVersion = false)
        }
    }

    fun selectManualVersion() {
        formState.update { it.copy(releaseId = null, manualVersion = true) }
    }

    // ---- 表单 ----

    fun onManualTitleChange(value: String) {
        formState.update { it.copy(releaseTitle = value) }
    }

    fun onPriceChange(value: String) {
        formState.update { it.copy(priceText = value) }
    }

    fun onCurrencyChange(code: String) {
        formState.update { it.copy(currency = Money.normalize(code)) }
    }

    fun onConditionChange(condition: CopyCondition) {
        formState.update { it.copy(condition = condition) }
    }

    fun onConditionNoteChange(value: String) {
        formState.update { it.copy(conditionNote = value) }
    }

    fun onPurchaseDateChange(date: LocalDate?) {
        formState.update { it.copy(purchaseDate = date) }
    }

    fun onShopChange(value: String) {
        formState.update { it.copy(shop = value) }
    }

    fun onNotesChange(value: String) {
        formState.update { it.copy(notes = value) }
    }

    fun onQuantityChange(value: Int) {
        formState.update { it.copy(quantity = value.coerceIn(1, 99)) }
    }

    fun onOrderChange(orderId: Long?) {
        formState.update { it.copy(orderId = orderId) }
    }

    // ---- 保存 ----

    fun save() {
        val vn = selectedVn.value ?: return
        val form = formState.value
        if (!form.canSave || savingState.value) return

        viewModelScope.launch {
            savingState.value = true
            try {
                val now = System.currentTimeMillis()
                val release = form.releaseId?.let { id -> releasesState.value.releases.firstOrNull { it.id == id } }
                val coverUrl = release?.displayImage() ?: vn.imageUrl

                val copies = (1..form.quantity).map { index ->
                    OwnedCopy(
                        id = 0,
                        vnId = vn.id,
                        releaseId = form.releaseId,
                        vnTitle = vn.title,
                        releaseTitle = form.releaseTitle.takeIf { it.isNotBlank() },
                        coverUrl = coverUrl,
                        priceMinor = form.parsedPrice ?: 0L,
                        currency = Money.normalize(form.currency),
                        condition = form.condition,
                        conditionNote = form.conditionNote.takeIf { it.isNotBlank() },
                        purchaseDate = form.purchaseDate,
                        shop = form.shop.takeIf { it.isNotBlank() },
                        orderId = form.orderId,
                        notes = form.notes.takeIf { it.isNotBlank() },
                        createdAt = now + index,
                        updatedAt = now + index,
                    )
                }
                val ids = collectionRepository.addCopies(copies)
                eventsChannel.send(AddFlowEvent.Saved(ids))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                eventsChannel.send(AddFlowEvent.Failed(e.message ?: "保存失败"))
            } finally {
                savingState.value = false
            }
        }
    }
}
