package com.vnventory.app.ui.collection

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.CollectionSort
import com.vnventory.app.domain.model.versionKey
import com.vnventory.app.domain.model.copyOrdinal
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.LoadingState
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.ui.components.OwnedCoverCard
import com.vnventory.app.ui.components.OwnedListCard
import com.vnventory.app.ui.components.PageHeader
import com.vnventory.app.ui.components.ShelfFab
import com.vnventory.app.ui.theme.ShelfMotion
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.vnventory.app.R
import com.vnventory.app.ui.text.localized

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CollectionScreen(
    onAddClick: () -> Unit,
    onCopyClick: (Long) -> Unit,
    viewModel: CollectionViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CollectionContent(state, viewModel::onSearchChange, viewModel::onSortChange, onAddClick, onCopyClick, error = { OperationError(viewModel) })
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CollectionContent(
    state: CollectionUiState,
    onSearchChange: (String) -> Unit,
    onSortChange: (CollectionSort) -> Unit,
    onAddClick: () -> Unit,
    onCopyClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    error: @Composable () -> Unit = {},
) {
    var isGrid by rememberSaveable { mutableStateOf(true) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    val expandedFab by remember {
        derivedStateOf {
            if (isGrid) gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 24
            else listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 24
        }
    }
    val counts = remember(state.allCopies) { state.allCopies.groupingBy { it.versionKey() }.eachCount() }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)) {
                PageHeader(stringResource(R.string.collection_title), subtitle = null, eyebrow = stringResource(R.string.collection_eyebrow))
                Spacer(Modifier.height(16.dp))
                TextField(
                    value = state.query.search,
                    onValueChange = onSearchChange,
                    placeholder = { Text(stringResource(R.string.collection_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (state.query.search.isNotEmpty()) ({
                        IconButton(onClick = { onSearchChange(""); focus.clearFocus() }) { Icon(Icons.Default.Clear, stringResource(R.string.search_clear)) }
                    }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SortMenu(state.query.sort, onSortChange, Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow(Modifier.width(104.dp)) {
                        SegmentedButton(isGrid, { focus.clearFocus(); isGrid = true }, SegmentedButtonDefaults.itemShape(0, 2), icon = {}) {
                            Icon(painterResource(R.drawable.ic_ui_grid), contentDescription = stringResource(R.string.collection_grid), modifier = Modifier.size(20.dp))
                        }
                        SegmentedButton(!isGrid, { focus.clearFocus(); isGrid = false }, SegmentedButtonDefaults.itemShape(1, 2), icon = {}) {
                            Icon(painterResource(R.drawable.ic_ui_list), contentDescription = stringResource(R.string.collection_list), modifier = Modifier.size(20.dp))
                        }
                    }
                }
                error()
            }
            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> LoadingState(message = stringResource(R.string.collection_loading))
                    state.isEmpty && state.isSearching -> EmptyState(stringResource(R.string.collection_no_matches), subtitle = stringResource(R.string.collection_no_matches_hint), actionLabel = stringResource(R.string.search_clear), onAction = { onSearchChange("") })
                    state.isEmpty -> EmptyState(stringResource(R.string.collection_empty_title), subtitle = stringResource(R.string.collection_empty_hint), actionLabel = stringResource(R.string.add_first_copy), onAction = onAddClick)
                    else -> AnimatedContent(
                        targetState = isGrid,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = { fadeIn(tween(ShelfMotion.Standard, delayMillis = 70)) togetherWith fadeOut(tween(ShelfMotion.Quick)) },
                        label = "collectionView",
                    ) { grid ->
                        if (grid) LazyVerticalGrid(
                            columns = GridCells.Adaptive(148.dp),
                            state = gridState,
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 104.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(state.copies, key = { it.id }, contentType = { "ownedCover" }) { copy ->
                                OwnedCoverCard(copy, { onCopyClick(copy.id) }, Modifier.animateItem(), counts[copy.versionKey()] ?: 1, state.showPrices, state.allCopies.copyOrdinal(copy))
                            }
                            if (state.isSearching) item(key = "searchSummary") {
                                Text(pluralStringResource(R.plurals.collection_shown_count, state.copies.size, state.copies.size), Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 104.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.copies, key = { it.id }, contentType = { "ownedRow" }) { copy ->
                                OwnedListCard(copy, { onCopyClick(copy.id) }, Modifier.animateItem(), state.showPrices, state.allCopies.copyOrdinal(copy))
                            }
                            if (state.isSearching) item(key = "searchSummary") {
                                Text(pluralStringResource(R.plurals.collection_shown_count, state.copies.size, state.copies.size), Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (!state.isEmpty) ShelfFab(onAddClick, Modifier.align(Alignment.BottomEnd).padding(20.dp), expanded = expandedFab)
    }
}

@Composable
private fun SortMenu(current: CollectionSort, onSelect: (CollectionSort) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.collection_sort, current.label.localized())
    Box(modifier) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
            Icon(painterResource(R.drawable.ic_ui_sort), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(current.label.localized(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded, { expanded = false }) {
            CollectionSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label.localized()) },
                    leadingIcon = { if (sort == current) Icon(Icons.Default.Check, contentDescription = stringResource(R.string.collection_sort_current)) },
                    onClick = { expanded = false; onSelect(sort) },
                )
            }
        }
    }
}
