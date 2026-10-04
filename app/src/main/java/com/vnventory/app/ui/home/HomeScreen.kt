package com.vnventory.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.domain.model.copyOrdinal
import com.vnventory.app.ui.components.CostHighlight
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.MoneyTotalsInline
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.ui.components.OwnedCoverCard
import com.vnventory.app.ui.components.PageHeader
import com.vnventory.app.ui.components.SectionCard
import com.vnventory.app.ui.components.SectionHeading
import com.vnventory.app.ui.components.ShelfIconTile
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.stringResource
import com.vnventory.app.R

@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onCopyClick: (Long) -> Unit,
    onSeeAllClick: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val recent by viewModel.recentCopies.collectAsStateWithLifecycle()
    HomeContent(stats, recent, onAddClick, onCopyClick, onSeeAllClick, error = { OperationError(viewModel) })
}

/** 无 ViewModel 的展示层，便于 Preview、深浅主题和大字体的实际渲染测试。 */
@Composable
fun HomeContent(
    stats: HomeStats,
    recent: List<OwnedCopy>,
    onAddClick: () -> Unit,
    onCopyClick: (Long) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: @Composable () -> Unit = {},
) {
    var showCosts by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // 窄屏仍留下一点下一张封面；宽屏不过度放大海报。
        val coverWidth = ((maxWidth - 60.dp) / 2).coerceIn(144.dp, 192.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "heading") {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PageHeader(stringResource(R.string.home_title), subtitle = null, modifier = Modifier.weight(1f))
                        FilledTonalIconButton(onClick = onAddClick, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_collection))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CollectionCount(stringResource(R.string.works), stats.vnCount, R.drawable.ic_ui_shelf, Modifier.weight(1f))
                        CollectionCount(stringResource(R.string.physical_copies), stats.copyCount, R.drawable.ic_ui_batch, Modifier.weight(1f))
                    }
                    error()
                }
            }
            item(key = "shelf") {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.recent_collection), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                        TextButton(onClick = onSeeAllClick) {
                            Text(stringResource(R.string.browse_all))
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (recent.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.home_empty_title),
                            subtitle = stringResource(R.string.home_empty_hint),
                            modifier = Modifier.fillMaxWidth(),
                            actionLabel = stringResource(R.string.add_first_copy),
                            onAction = onAddClick,
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(recent, key = { it.id }) { copy ->
                                OwnedCoverCard(copy, { onCopyClick(copy.id) }, Modifier.width(coverWidth).animateItem(),
                                    showPrice = stats.showShelfPrices, ordinal = stats.allCopies.ifEmpty { recent }.copyOrdinal(copy), compact = true)
                            }
                        }
                    }
                }
            }
            if (stats.showPriceStats) item(key = "costs") {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeading(stringResource(R.string.collection_spending), stringResource(R.string.collection_spending_hint))
                    CostHighlight(
                        stats.grandTotals,
                        stringResource(R.string.recorded_spending),
                        supporting = stringResource(R.string.price_coverage, stats.pricedCopyCount, stats.copyCount),
                    )
                    TextButton(onClick = { showCosts = !showCosts }, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(if (showCosts) R.string.spending_collapse else R.string.spending_expand))
                    }
                    AnimatedVisibility(showCosts, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        SectionCard {
                            CostRow(stringResource(R.string.goods_purchase), stats.priceTotals)
                            CostRow(stringResource(R.string.message_category_shipping), stats.shippingTotals)
                            CostRow(stringResource(R.string.message_category_fee), stats.feeTotals)
                            CostRow(stringResource(R.string.message_category_tax), stats.taxTotals)
                            CostRow(stringResource(R.string.message_category_other), stats.otherTotals)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionCount(label: String, count: Int, @DrawableRes icon: Int, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShelfIconTile(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(count.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CostRow(label: String, totals: Map<String, Long>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        MoneyTotalsInline(totals, style = MaterialTheme.typography.bodyLarge)
    }
}
