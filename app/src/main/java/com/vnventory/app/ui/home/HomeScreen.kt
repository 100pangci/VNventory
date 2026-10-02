package com.vnventory.app.ui.home

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.ui.components.MoneyTotalsInline
import com.vnventory.app.ui.components.SectionCard
import com.vnventory.app.ui.components.VnCover

@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onCopyClick: (Long) -> Unit,
    onSeeAllClick: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val recent by viewModel.recentCopies.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { GrandTotalCard(stats) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CountCard(
                        label = "收藏 VN",
                        value = stats.vnCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    CountCard(
                        label = "实体盒",
                        value = stats.copyCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item { CostBreakdownCard(stats) }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "最近购入",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onSeeAllClick) { Text("查看全部") }
                }
            }

            item {
                if (recent.isEmpty()) {
                    Text(
                        text = "还没有收藏。点右下角按钮，从 VNDB 搜索并添加第一个版本吧。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recent, key = { it.id }) { copy ->
                            RecentCopyCard(copy = copy, onClick = { onCopyClick(copy.id) })
                        }
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
private fun GrandTotalCard(stats: HomeStats) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "总支出",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            if (stats.grandTotals.isEmpty()) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                stats.grandTotals.entries.sortedBy { it.key }.forEach { (currency, amount) ->
                    Text(
                        text = Money.format(amount, currency),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "含购入成本与全部费用；多币种分开统计（不做汇率换算）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun CountCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun CostBreakdownCard(stats: HomeStats) {
    SectionCard(title = "支出构成") {
        BreakdownRow("购入成本", stats.priceTotals)
        BreakdownRow("运费", stats.shippingTotals)
        BreakdownRow("手续费", stats.feeTotals)
        BreakdownRow("税费", stats.taxTotals)
        BreakdownRow("其他", stats.otherTotals)
    }
}

@Composable
private fun BreakdownRow(label: String, totals: Map<String, Long>) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        MoneyTotalsInline(
            totals = totals,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RecentCopyCard(copy: OwnedCopy, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable(onClick = onClick),
    ) {
        VnCover(
            url = copy.coverUrl,
            contentDescription = copy.vnTitle,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            corner = 12.dp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = copy.vnTitle,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = Money.format(copy.priceMinor, copy.currency),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
