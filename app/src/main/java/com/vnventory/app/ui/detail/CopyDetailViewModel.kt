package com.vnventory.app.ui.detail

import com.vnventory.app.ui.ActionViewModel
import kotlinx.coroutines.flow.catch
import androidx.lifecycle.viewModelScope
import com.vnventory.app.di.AppContainer
import com.vnventory.app.domain.cost.CopyCost
import com.vnventory.app.domain.model.OrderDetail
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.domain.model.ReleaseInfo
import com.vnventory.app.domain.model.VnInfo
import com.vnventory.app.domain.model.copyOrdinal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class CopyDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val copy: OwnedCopy? = null,
    val release: ReleaseInfo? = null,
    val vn: VnInfo? = null,
    val orderDetail: OrderDetail? = null,
    val ordinal: Int? = null,
) {
    /** 若该盒属于某订单：实时计算的分摊后成本 */
    val cost: CopyCost? get() = copy?.let { orderDetail?.costFor(it.id) }
    val costIncomplete: Boolean get() = copy?.priceMinor == null ||
        orderDetail?.breakdown?.let { it.issues.isNotEmpty() || it.unallocatedTotals.isNotEmpty() } == true

    /** 展示用封面：缓存中的 Release 包装图 > 收藏快照 > VN 封面 */
    val coverUrl: String?
        get() = copy?.let { c ->
            release?.displayImage() ?: c.coverUrl ?: vn?.imageUrl
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CopyDetailViewModel(
    container: AppContainer,
    private val copyId: Long,
) : ActionViewModel() {

    private val collectionRepository = container.collectionRepository
    private val vnRepository = container.vnRepository
    private val purchaseRepository = container.purchaseRepository

    val uiState: StateFlow<CopyDetailUiState> = collectionRepository.observeById(copyId)
        .flatMapLatest { copy ->
            if (copy == null) {
                flowOf(CopyDetailUiState(loading = false, notFound = true))
            } else {
                combine(
                    vnRepository.observeCachedVn(copy.vnId),
                    vnRepository.observeCachedReleases(copy.vnId),
                    copy.orderId?.let { purchaseRepository.observeOrderDetail(it) } ?: flowOf(null),
                    collectionRepository.observeByVn(copy.vnId),
                ) { vn, releases, orderDetail, siblings ->
                    CopyDetailUiState(
                        loading = false,
                        copy = copy,
                        release = copy.releaseId?.let { rid -> releases.firstOrNull { it.id == rid } },
                        vn = vn,
                        orderDetail = orderDetail,
                        ordinal = siblings.copyOrdinal(copy),
                    )
                }
            }
        }
        .catch { reportError(it); emit(CopyDetailUiState(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CopyDetailUiState())

    fun delete(onDeleted: () -> Unit) {
        launchAction {
            collectionRepository.delete(copyId)
            onDeleted()
        }
    }
}
