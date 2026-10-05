package com.vnventory.app.ui.collection

import com.vnventory.app.ui.ActionViewModel
import androidx.lifecycle.viewModelScope
import com.vnventory.app.di.AppContainer
import com.vnventory.app.domain.model.CollectionQuery
import com.vnventory.app.domain.model.CollectionSort
import com.vnventory.app.domain.model.OwnedCopy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CollectionUiState(
    val query: CollectionQuery = CollectionQuery(),
    val copies: List<OwnedCopy> = emptyList(),
    val loading: Boolean = true,
    val showPrices: Boolean = false,
    val showReleaseNames: Boolean = false,
    val allCopies: List<OwnedCopy> = copies,
) {
    val isEmpty: Boolean get() = !loading && copies.isEmpty()
    val isSearching: Boolean get() = query.search.isNotBlank()
}

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModel(container: AppContainer) : ActionViewModel() {

    private val collectionRepository = container.collectionRepository

    private val query = MutableStateFlow(CollectionQuery())

    private val displayQuery = combine(query, container.settingsRepository.titleDisplayMode) { q, mode -> q.copy(titleDisplayMode = mode) }

    private val copiesFlow = displayQuery.flatMapLatest { q ->
        collectionRepository.observeCollection(q).catch { reportError(it); emit(emptyList()) }
    }

    val uiState: StateFlow<CollectionUiState> =
        combine(displayQuery, copiesFlow, container.settingsRepository.showShelfPrices, container.settingsRepository.showShelfReleaseNames, collectionRepository.observeCollection(CollectionQuery())) { q, copies, showPrices, showReleaseNames, allCopies ->
            CollectionUiState(query = q, copies = copies, loading = false, showPrices = showPrices, showReleaseNames = showReleaseNames, allCopies = allCopies)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState())

    fun onSearchChange(text: String) {
        query.update { it.copy(search = text) }
    }

    fun onSortChange(sort: CollectionSort) {
        query.update { it.copy(sort = sort) }
    }
}
