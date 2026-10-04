package com.vnventory.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.ui.components.CostHighlight
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.MoneyTotalsInline
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.ui.components.OwnedCoverCard
import com.vnventory.app.ui.components.PageHeader
import com.vnventory.app.ui.components.SectionCard
import com.vnventory.app.ui.components.SectionHeading
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
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 24.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "heading") {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    PageHeader(stringResource(R.string.home_title), stringResource(R.string.home_subtitle))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CollectionCount(stringResource(R.string.works), stats.vnCount, Modifier.weight(1f))
                        CollectionCount(stringResource(R.string.physical_copies), stats.copyCount, Modifier.weight(1f))
                    }
                    error()
                }
            }
            item(key = "recentTitle") {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeading(stringResource(R.string.recent_collection), stringResource(R.string.recent_collection_hint))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onAddClick, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.add_collection_shortcut)) }
                        TextButton(onClick = onSeeAllClick, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.browse_all)) }
                    }
                }
            }
            item(key = "shelf") {
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
                            OwnedCoverCard(copy, { onCopyClick(copy.id) }, Modifier.width(164.dp).animateItem())
                        }
                    }
                }
            }
            item(key = "costs") {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeading(stringResource(R.string.collection_spending), stringResource(R.string.collection_spending_hint))
                    CostHighlight(
                        stats.grandTotals,
                        stringResource(R.string.all_spending),
                        supporting = stringResource(R.string.all_spending_hint),
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
private fun CollectionCount(label: String, count: Int, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
