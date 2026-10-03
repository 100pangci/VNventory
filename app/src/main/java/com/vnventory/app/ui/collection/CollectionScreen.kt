package com.vnventory.app.ui.collection

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.CollectionSort
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.LoadingState
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.ui.components.OwnedCoverCard
import com.vnventory.app.ui.components.OwnedListCard
import com.vnventory.app.ui.components.PageHeader
import com.vnventory.app.ui.components.ShelfFab
import com.vnventory.app.ui.theme.ShelfMotion

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
    val counts = remember(state.copies) { state.copies.mapNotNull { it.releaseId }.groupingBy { it }.eachCount() }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
                PageHeader("我的书架", if (state.isSearching) "找到 ${state.copies.size} 盒匹配的收藏" else "${state.copies.size} 盒实体 · 按版本与每盒独立记录", eyebrow = "COLLECTION")
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.SearchBar(
                    inputField = {
                        androidx.compose.material3.SearchBarDefaults.InputField(
                            query = state.query.search,
                            onQueryChange = onSearchChange,
                            onSearch = { focus.clearFocus() },
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = { Text("搜作品、版本或店铺") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = if (state.query.search.isNotEmpty()) ({
                                IconButton(onClick = { onSearchChange(""); focus.clearFocus() }) { Icon(Icons.Default.Clear, "清除搜索") }
                            }) else null,
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp,
                ) {}
                Spacer(Modifier.height(8.dp))
                // 排序和视图切换分两行，避免窄屏/大字体互相挤压。
                SortMenu(state.query.sort, onSortChange)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(isGrid, { focus.clearFocus(); isGrid = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("封面书架") }
                    SegmentedButton(!isGrid, { focus.clearFocus(); isGrid = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("详细列表") }
                }
                error()
            }
            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> LoadingState(message = "整理书架…")
                    state.isEmpty && state.isSearching -> EmptyState("没有匹配的收藏", subtitle = "试试作品原名、版本名称或购买店铺。", actionLabel = "清除搜索", onAction = { onSearchChange("") })
                    state.isEmpty -> EmptyState("给喜欢的作品留一个位置", subtitle = "选择具体发行版本，把第一盒收藏放上书架。", actionLabel = "添加第一盒", onAction = onAddClick)
                    else -> AnimatedContent(
                        targetState = isGrid,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = { fadeIn(tween(ShelfMotion.Standard, delayMillis = 70)) togetherWith fadeOut(tween(ShelfMotion.Quick)) },
                        label = "collectionView",
                    ) { grid ->
                        if (grid) LazyVerticalGrid(
                            columns = GridCells.Adaptive(148.dp),
                            state = gridState,
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 104.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(state.copies, key = { it.id }, contentType = { "ownedCover" }) { copy ->
                                OwnedCoverCard(copy, { onCopyClick(copy.id) }, Modifier.animateItem(), counts[copy.releaseId] ?: 1)
                            }
                            if (state.isSearching) item(key = "searchSummary") {
                                Text("已显示 ${state.copies.size} 盒匹配的收藏", Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 104.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.copies, key = { it.id }, contentType = { "ownedRow" }) { copy ->
                                OwnedListCard(copy, { onCopyClick(copy.id) }, Modifier.animateItem())
                            }
                            if (state.isSearching) item(key = "searchSummary") {
                                Text("已显示 ${state.copies.size} 盒匹配的收藏", Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun SortMenu(current: CollectionSort, onSelect: (CollectionSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("排序 · ${current.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded, { expanded = false }) {
            CollectionSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    leadingIcon = { if (sort == current) Icon(Icons.Default.Check, contentDescription = "当前排序") },
                    onClick = { expanded = false; onSelect(sort) },
                )
            }
        }
    }
}
