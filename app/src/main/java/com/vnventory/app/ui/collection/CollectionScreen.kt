package com.vnventory.app.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.CollectionSort
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.LoadingState
import com.vnventory.app.ui.components.MoneyAmountText
import com.vnventory.app.ui.components.Tag
import com.vnventory.app.ui.components.VnCover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    onAddClick: () -> Unit,
    onCopyClick: (Long) -> Unit,
    viewModel: CollectionViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isGrid by rememberSaveable { mutableStateOf(true) }

    // 同 Release 多盒角标
    val copiesPerRelease: Map<String, Int> = remember(state.copies) {
        state.copies
            .mapNotNull { it.releaseId }
            .groupingBy { it }
            .eachCount()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = state.query.search,
                    onValueChange = viewModel::onSearchChange,
                    placeholder = { Text("搜索标题 / 版本 / 店铺") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.search.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SortMenu(
                        current = state.query.sort,
                        onSelect = viewModel::onSortChange,
                        modifier = Modifier.weight(1f),
                    )
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = isGrid,
                            onClick = { isGrid = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text("网格") }
                        SegmentedButton(
                            selected = !isGrid,
                            onClick = { isGrid = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text("列表") }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${state.copies.size} 盒",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                state.loading -> LoadingState()

                state.isEmpty && state.isSearching -> EmptyState(
                    title = "没有找到匹配的收藏",
                    subtitle = "换个关键词，或检查是否还没有添加",
                )

                state.isEmpty -> EmptyState(
                    title = "还没有收藏",
                    subtitle = "点右下角按钮，从 VNDB 搜索并添加",
                )

                isGrid -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.copies, key = { it.id }) { copy ->
                        CopyGridCard(
                            copy = copy,
                            sameReleaseCount = copiesPerRelease[copy.releaseId] ?: 1,
                            onClick = { onCopyClick(copy.id) },
                        )
                    }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                ) {
                    items(state.copies, key = { it.id }) { copy ->
                        CopyListRow(
                            copy = copy,
                            sameReleaseCount = copiesPerRelease[copy.releaseId] ?: 1,
                            onClick = { onCopyClick(copy.id) },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加收藏")
        }
    }
}

@Composable
private fun SortMenu(
    current: CollectionSort,
    onSelect: (CollectionSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text("排序：${current.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CollectionSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    },
                )
            }
        }
    }
}

@Composable
private fun CopyGridCard(
    copy: OwnedCopy,
    sameReleaseCount: Int,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box {
            VnCover(
                url = copy.coverUrl,
                contentDescription = copy.vnTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f),
            )
            if (sameReleaseCount > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    Tag(text = "×$sameReleaseCount", emphasized = true)
                }
            }
            if (copy.isManualRelease) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                ) {
                    Tag(text = "手动")
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = copy.vnTitle,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = copy.displayReleaseName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoneyAmountText(
                minor = copy.priceMinor,
                currency = copy.currency,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Tag(text = copy.condition.label)
        }
    }
}

@Composable
private fun CopyListRow(
    copy: OwnedCopy,
    sameReleaseCount: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        VnCover(
            url = copy.coverUrl,
            contentDescription = copy.vnTitle,
            modifier = Modifier
                .width(56.dp)
                .height(80.dp),
            corner = 8.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = copy.vnTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = copy.displayReleaseName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(text = copy.condition.label)
                if (copy.isManualRelease) Tag(text = "手动")
                if (sameReleaseCount > 1) Tag(text = "×$sameReleaseCount", emphasized = true)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            MoneyAmountText(
                minor = copy.priceMinor,
                currency = copy.currency,
                style = MaterialTheme.typography.titleSmall,
            )
            copy.shop?.let { shop ->
                Text(
                    text = shop,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
