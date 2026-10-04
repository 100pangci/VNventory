package com.vnventory.app.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.OrderSummary
import com.vnventory.app.ui.components.CurrencySelector
import com.vnventory.app.ui.components.DateField
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.LoadingState
import com.vnventory.app.ui.components.MoneyTotalsInline
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.ui.components.PageHeader
import com.vnventory.app.ui.components.ShelfFab
import com.vnventory.app.ui.components.BrandMark
import com.vnventory.app.ui.components.PressableSurface
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.vnventory.app.R
import com.vnventory.app.ui.components.ShopChannelField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    onOrderClick: (Long) -> Unit,
    onOrderCreated: (Long) -> Unit,
    viewModel: OrdersViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val expandedFab by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 24 } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        PageHeader(stringResource(R.string.orders_title), stringResource(R.string.orders_hint), Modifier.padding(24.dp), eyebrow = stringResource(R.string.orders_eyebrow))
        if (!state.createOpen) OperationError(viewModel)
        Box(Modifier.weight(1f)) {
        when {
            state.loading -> LoadingState()

            state.orders.isEmpty() -> EmptyState(
                title = stringResource(R.string.orders_empty_title),
                subtitle = stringResource(R.string.orders_empty_hint),
                actionLabel = stringResource(R.string.orders_create_first),
                onAction = viewModel::openCreate,
            )

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.orders, key = { it.order.id }) { summary ->
                    OrderCard(summary = summary, modifier = Modifier.animateItem(), onClick = { onOrderClick(summary.order.id) })
                }
            }
        }
        }
        }
        if (state.orders.isNotEmpty() || state.loading) {
            ShelfFab(viewModel::openCreate, Modifier.align(Alignment.BottomEnd).padding(20.dp), label = stringResource(R.string.order_create), expanded = expandedFab)
        }
    }

    if (state.createOpen) {
        AlertDialog(
            onDismissRequest = viewModel::closeCreate,
            title = { Text(stringResource(R.string.order_create_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OperationError(viewModel)
                    OutlinedTextField(
                        value = state.form.title,
                        onValueChange = viewModel::onTitleChange,
                        label = { Text(stringResource(R.string.order_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ShopChannelField(
                        value = state.form.merchant,
                        onValueChange = viewModel::onMerchantChange,
                        options = state.shopChannels,
                        label = stringResource(R.string.order_merchant_optional),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DateField(
                            date = state.form.date,
                            onDateChange = viewModel::onDateChange,
                            placeholder = stringResource(R.string.order_date),
                        )
                        Spacer(Modifier.width(8.dp))
                        CurrencySelector(
                            selected = state.form.currency,
                            onSelect = viewModel::onCurrencyChange,
                        )
                    }
                    OutlinedTextField(
                        value = state.form.notes,
                        onValueChange = viewModel::onNotesChange,
                        label = { Text(stringResource(R.string.notes_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createOrder(onOrderCreated) },
                    enabled = state.form.canSave && !state.creating,
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeCreate) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun OrderCard(summary: OrderSummary, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val separator = stringResource(R.string.separator_dot)
    val copyCount = pluralStringResource(R.plurals.order_copy_count, summary.copyCount, summary.copyCount)
    PressableSurface(onClick, modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrandMark()
            Text(
                text = summary.order.title,
                style = MaterialTheme.typography.titleMedium,
            )
            }
            val subtitle = buildString {
                summary.order.merchant?.let { append(it) }
                summary.order.orderDate?.let {
                    if (isNotEmpty()) append(separator)
                    append(it)
                }
                if (isNotEmpty()) append(separator)
                append(copyCount)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.order_spending), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyTotalsInline(
                totals = summary.grandTotals,
                style = MaterialTheme.typography.titleMedium,
            )
            if (summary.pricedCopyCount < summary.copyCount) Text(
                stringResource(R.string.price_coverage, summary.pricedCopyCount, summary.copyCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.feeTotals.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.order_fee_included, summary.feeTotals.entries.sortedBy { it.key }.joinToString(stringResource(R.string.separator_plus)) {
                        com.vnventory.app.domain.model.Money.format(it.value, it.key)
                    }),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
