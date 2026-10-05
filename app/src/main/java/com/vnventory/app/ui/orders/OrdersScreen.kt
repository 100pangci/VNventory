package com.vnventory.app.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
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
        OrderCreateDialog(state, viewModel::onTitleChange, viewModel::onMerchantChange, viewModel::onDateChange,
            viewModel::onCurrencyChange, viewModel::onNotesChange, viewModel::closeCreate,
            { viewModel.createOrder(onOrderCreated) }, error = { OperationError(viewModel) })
    }
}

@Composable
internal fun OrderCreateDialog(
    state: OrdersUiState,
    onTitleChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onDateChange: (java.time.LocalDate?) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    error: @Composable () -> Unit = {},
    editing: Boolean = false,
) {
    val focus = LocalFocusManager.current
    BackHandler(enabled = state.creating) { /* 写入期间保留弹窗。 */ }
    // 可编辑下拉不参与 AlertDialog 的固有尺寸测量；限制高度并固定底部操作。
    Dialog(onDismissRequest = { if (!state.creating) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(24.dp).widthIn(max = 560.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.heightIn(max = 640.dp)) {
                Text(stringResource(if (editing) R.string.order_edit_title else R.string.order_create_title), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(24.dp).semantics { heading() })
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    error()
                    OutlinedTextField(
                        value = state.form.title,
                        onValueChange = onTitleChange,
                        label = { Text(stringResource(R.string.order_name_hint)) },
                        singleLine = true,
                        enabled = !state.creating,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ShopChannelField(
                        value = state.form.merchant,
                        onValueChange = onMerchantChange,
                        options = state.shopChannels,
                        label = stringResource(R.string.order_merchant_optional),
                        enabled = !state.creating,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DateField(
                            date = state.form.date,
                            onDateChange = onDateChange,
                            placeholder = stringResource(R.string.order_date),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.creating,
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.currency), style = MaterialTheme.typography.bodyMedium)
                            CurrencySelector(selected = state.form.currency, onSelect = onCurrencyChange, enabled = !state.creating)
                        }
                    }
                    if (editing) Text(stringResource(R.string.order_currency_edit_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = state.form.notes,
                        onValueChange = onNotesChange,
                        label = { Text(stringResource(R.string.notes_optional)) },
                        singleLine = true,
                        enabled = !state.creating,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                FlowRow(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !state.creating) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = { focus.clearFocus(); onCreate() }, enabled = state.form.canSave && !state.creating) {
                        Text(stringResource(if (state.creating) R.string.saving else if (editing) R.string.action_save else R.string.action_create))
                    }
                }
            }
        }
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
