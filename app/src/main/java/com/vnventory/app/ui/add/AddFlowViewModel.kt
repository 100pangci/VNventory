package com.vnventory.app.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnventory.app.core.AppResult
import com.vnventory.app.di.AppContainer
import com.vnventory.app.data.repository.CollectionRepository
import com.vnventory.app.data.repository.PurchaseRepository
import com.vnventory.app.data.repository.SettingsRepository
import com.vnventory.app.data.repository.VnRepository
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OrderSummary
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.domain.model.ReleaseInfo
import com.vnventory.app.domain.model.VnInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.vnventory.app.core.toAppError
import com.vnventory.app.domain.text.Message
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message

// ---------------------------------------------------------------------------
// UI 状态
// ---------------------------------------------------------------------------

data class VnSearchUiState(
    val query: String = "",
    val results: List<VnInfo> = emptyList(),
    val loading: Boolean = false,
    val error: Message? = null,
    val offline: Boolean = false,
    val hasSearched: Boolean = false,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

data class ReleasesUiState(
    val loading: Boolean = false,
    val error: Message? = null,
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

    /** 空白为未记录；非空则必须可解析（包括真实的 0）。 */
    val priceValid: Boolean get() = priceText.isBlank() || parsedPrice != null

    val quantityValid: Boolean get() = quantity in 1..99

    val manualVersionValid: Boolean get() = !manualVersion || releaseTitle.isNotBlank()

    /** 商品本体小计，不含购买批次费用，仍采用检查加法。 */
    val subtotalMinor: Long?
        get() = if (priceValid && quantityValid && parsedPrice != null) {
            runCatching { Money.sum(List(quantity) { requireNotNull(parsedPrice) }) }.getOrNull()
        } else null

    val canSave: Boolean get() = priceValid && quantityValid && manualVersionValid &&
        (priceText.isBlank() || subtotalMinor != null) && (condition != CopyCondition.CUSTOM || conditionNote.isNotBlank())
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
    val shopChannels: List<String> = emptyList(),
)

sealed interface AddFlowEvent {
    data class Saved(val copyIds: List<Long>) : AddFlowEvent
    data class Failed(val message: Message) : AddFlowEvent
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class AddFlowViewModel(
    private val vnRepository: VnRepository,
    private val collectionRepository: CollectionRepository,
    private val purchaseRepository: PurchaseRepository,
    private val settingsRepository: SettingsRepository,
    private val orderIdArg: Long?,
) : ViewModel() {
    constructor(container: AppContainer, orderIdArg: Long?) : this(
        container.vnRepository, container.collectionRepository, container.purchaseRepository, container.settingsRepository, orderIdArg,
    )
    private var searchJob: Job? = null
    private var releaseJob: Job? = null
    private var searchGeneration = 0
    private var releaseGeneration = 0
    private var formTouched = false

    private val searchState = MutableStateFlow(VnSearchUiState())
    private val selectedVn = MutableStateFlow<VnInfo?>(null)
    private val releasesState = MutableStateFlow(ReleasesUiState())
    private val formState = MutableStateFlow(PurchaseFormState(orderId = orderIdArg))
    private val savingState = MutableStateFlow(false)

    private val eventsChannel = Channel<AddFlowEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<AddFlowUiState> = combine(
        combine(searchState, selectedVn, releasesState) { search, vn, releases ->
            Triple(search, vn, releases)
        },
        combine(formState, savingState) { form, saving -> form to saving },
        combine(
            purchaseRepository.observeOrders(),
            settingsRepository.shopChannels.catch {
                if (it is CancellationException) throw it
                eventsChannel.send(AddFlowEvent.Failed(it.toAppError().message))
                emit(emptyList())
            },
        ) { orders, shops -> orders to shops },
    ) { (search, vn, releases), (form, saving), (orders, shops) ->
        AddFlowUiState(
            orderContextId = orderIdArg,
            orders = orders,
            search = search,
            selectedVn = vn,
            releases = releases,
            form = form,
            saving = saving,
            shopChannels = shops,
        )
    }.catch {
        if (it is CancellationException) throw it
        eventsChannel.send(AddFlowEvent.Failed(it.toAppError().message))
        emit(AddFlowUiState())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddFlowUiState())

    init {
        // 默认货币：订单上下文优先，否则用设置里的默认货币
        viewModelScope.launch {
            try {
                val defaultCurrency = settingsRepository.defaultCurrency.first()
                val orderCurrency = orderIdArg?.let { purchaseRepository.getOrder(it)?.currency }
                if (!formTouched) formState.update { it.copy(currency = orderCurrency ?: defaultCurrency, orderId = orderIdArg) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                eventsChannel.send(AddFlowEvent.Failed(e.toAppError().message))
            }
        }
    }

    // ---- 搜索 ----

    fun onQueryChange(text: String) {
        searchGeneration++
        searchJob?.cancel()
        searchState.value = VnSearchUiState(query = text, loading = text.isNotBlank())
        if (text.isNotBlank()) requestSearch(page = 1, debounce = true)
    }

    fun retrySearch() = requestSearch(page = if (searchState.value.results.isEmpty()) 1 else searchState.value.page + 1)

    fun loadMore() {
        val state = searchState.value
        if (state.hasMore && !state.loading && !state.loadingMore) requestSearch(state.page + 1)
    }

    private fun requestSearch(page: Int, debounce: Boolean = false) {
        searchJob?.cancel()
        val generation = ++searchGeneration
        val query = searchState.value.query
        if (query.isBlank()) return
        searchState.update { it.copy(loading = page == 1, loadingMore = page > 1, error = null) }
        searchJob = viewModelScope.launch {
            if (debounce) delay(350)
            val result = vnRepository.searchVn(query, page)
            if (generation != searchGeneration) return@launch
            when (result) {
                is AppResult.Success -> searchState.update {
                    it.copy(
                        results = ((if (page > 1) it.results else emptyList()) + result.data.items).distinctBy { vn -> vn.id },
                        offline = result.data.offline,
                        loading = false,
                        loadingMore = false,
                        hasSearched = true,
                        page = page,
                        hasMore = result.data.hasMore,
                    )
                }

                is AppResult.Failure -> searchState.update {
                    it.copy(loading = false, loadingMore = false, hasSearched = true, error = result.error.message)
                }
            }
        }
    }

    // ---- 选 VN / Release ----

    fun selectVn(vn: VnInfo) {
        releaseJob?.cancel()
        val generation = ++releaseGeneration
        selectedVn.value = vn
        formState.update { it.copy(releaseId = null, releaseTitle = "", manualVersion = false) }
        releasesState.value = ReleasesUiState(loading = true)
        releaseJob = viewModelScope.launch {
            try {
                // 先展示本地缓存，但所有异步结果都必须属于当前选择。
                val cached = vnRepository.observeCachedReleases(vn.id).first()
                if (generation != releaseGeneration) return@launch
                if (cached.isNotEmpty()) {
                    releasesState.update { it.copy(releases = cached) }
                }
                val result = vnRepository.fetchReleases(vn.id)
                if (generation != releaseGeneration || selectedVn.value?.id != vn.id) return@launch
                when (result) {
                    is AppResult.Success -> releasesState.update {
                        it.copy(releases = result.data, loading = false, error = null)
                    }

                    is AppResult.Failure -> releasesState.update {
                        it.copy(loading = false, error = result.error.message)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == releaseGeneration) releasesState.update { it.copy(loading = false, error = e.toAppError().message) }
            }
        }
    }

    fun backToSearch() {
        releaseGeneration++
        releaseJob?.cancel()
        selectedVn.value = null
        releasesState.value = ReleasesUiState()
    }

    /** 从‘购入信息’返回‘选择版本’ */
    fun backToReleases() {
        formState.update { it.copy(releaseId = null, manualVersion = false, releaseTitle = "") }
    }

    fun selectRelease(release: ReleaseInfo) {
        if (release.official == false || release.vnId != selectedVn.value?.id ||
            releasesState.value.releases.none { it.id == release.id && it.vnId == release.vnId && it.official != false }) return
        formState.update {
            it.copy(releaseId = release.id, releaseTitle = release.displayTitle(com.vnventory.app.domain.model.TitleDisplayMode.ORIGINAL), manualVersion = false)
        }
    }

    fun selectManualVersion() {
        formState.update { it.copy(releaseId = null, manualVersion = true) }
    }

    // ---- 表单 ----

    fun onPurchaseFormChange(value: PurchaseFormState) {
        formTouched = true
        formState.update { current ->
            value.copy(
                releaseId = current.releaseId,
                manualVersion = current.manualVersion,
                releaseTitle = if (current.manualVersion) value.releaseTitle else current.releaseTitle,
                currency = Money.normalize(value.currency),
                quantity = value.quantity.coerceIn(1, 99),
            )
        }
    }

    fun onManualTitleChange(value: String) {
        formState.update { it.copy(releaseTitle = value) }
    }

    fun onPriceChange(value: String) {
        formTouched = true
        formState.update { it.copy(priceText = value) }
    }

    fun onCurrencyChange(code: String) {
        formTouched = true
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
        formTouched = true
        formState.update { it.copy(orderId = orderId) }
    }

    // ---- 保存 ----

    fun save() {
        val vn = selectedVn.value ?: return
        val form = formState.value
        if (!form.canSave || savingState.value) return
        if (!form.manualVersion && (form.releaseId == null || releasesState.value.releases.none { it.id == form.releaseId && it.vnId == vn.id })) {
            eventsChannel.trySend(AddFlowEvent.Failed(message(MessageKey.RELEASE_SELECTION_MISMATCH)))
            return
        }

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
                        // Fixed compatibility snapshot, never the current UI display preference.
                        vnTitle = vn.displayTitle(com.vnventory.app.domain.model.TitleDisplayMode.ORIGINAL),
                        vnOriginalTitle = vn.originalTitle,
                        vnRomanizedTitle = vn.romanizedTitle,
                        releaseOriginalTitle = release?.originalTitle,
                        releaseRomanizedTitle = release?.romanizedTitle,
                        releaseTitle = form.releaseTitle.takeIf { it.isNotBlank() },
                        coverUrl = coverUrl,
                        priceMinor = form.parsedPrice,
                        currency = Money.normalize(form.currency),
                        condition = form.condition,
                        conditionNote = form.conditionNote.takeIf { it.isNotBlank() },
                        purchaseDate = form.purchaseDate,
                        shop = form.shop.trim().takeIf { it.isNotBlank() },
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
            } catch (e: Exception) {
                eventsChannel.send(AddFlowEvent.Failed(e.toAppError().message))
            } finally {
                savingState.value = false
            }
        }
    }
}
