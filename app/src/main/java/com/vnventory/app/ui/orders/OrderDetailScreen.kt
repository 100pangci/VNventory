package com.vnventory.app.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.cost.CopyCost
import com.vnventory.app.domain.cost.CostEngine
import com.vnventory.app.domain.cost.CostExpenseInput
import com.vnventory.app.domain.cost.CostCopyInput
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.Expense
import com.vnventory.app.domain.model.ExpenseCategory
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OrderDetail
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.domain.model.knownPriceTotals
import com.vnventory.app.domain.model.copyOrdinal
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.LabeledRow
import com.vnventory.app.ui.components.LoadingState
import com.vnventory.app.ui.components.SectionCard
import com.vnventory.app.ui.components.Tag
import com.vnventory.app.ui.components.VnCover
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.domain.cost.OrderCostBreakdown
import com.vnventory.app.ui.components.SectionHeading
import com.vnventory.app.ui.components.FormSaveBar
import com.vnventory.app.ui.components.FormSection
import com.vnventory.app.ui.components.MoneyInputField
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.vnventory.app.R
import com.vnventory.app.ui.text.localized

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    onBack: () -> Unit,
    onAddCopies: (Long) -> Unit,
    onCopyClick: (Long) -> Unit,
    viewModel: OrderDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDeleteOrder by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var copyToRemove by remember { mutableStateOf<OwnedCopy?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (!state.editor.open) OperationError(viewModel) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.detail?.order?.title ?: stringResource(R.string.nav_orders),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.detail != null) {
                        IconButton(onClick = { confirmDeleteOrder = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.order_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val detail = state.detail
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(padding))

            state.notFound || detail == null -> EmptyState(
                title = stringResource(R.string.order_not_found),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item { OrderHeaderCard(detail) }

                item {
                    SectionHeading(stringResource(R.string.order_collection), pluralStringResource(R.plurals.order_collection_count, detail.copies.size, detail.copies.size), stringResource(R.string.order_add_copy)) { onAddCopies(detail.order.id) }
                }

                if (detail.copies.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.order_no_copies),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(detail.copies, key = { it.id }) { copy ->
                    OrderCopyRow(
                        copy = copy,
                        cost = detail.costFor(copy.id),
                        ordinal = detail.copies.copyOrdinal(copy),
                        onClick = { onCopyClick(copy.id) },
                        onRemove = { copyToRemove = copy },
                        modifier = Modifier.animateItem(),
                    )
                }

                item {
                    SectionHeading(stringResource(R.string.order_expenses), pluralStringResource(R.plurals.order_expense_count, detail.expenses.size, detail.expenses.size), stringResource(R.string.expense_add), viewModel::openNewExpense)
                }

                if (detail.expenses.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.expense_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(detail.expenses, key = { it.id }) { expense ->
                    ExpenseRow(
                        expense = expense,
                        onClick = { viewModel.openEditExpense(expense) },
                        onDelete = { expenseToDelete = expense },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (state.editor.open) {
        ExpenseEditorSheet(state = state, viewModel = viewModel)
    }

    if (confirmDeleteOrder) {
        AlertDialog(
            onDismissRequest = { confirmDeleteOrder = false },
            title = { Text(stringResource(R.string.order_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.order_delete_hint, state.detail?.copies?.size ?: 0),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteOrder = false
                    viewModel.deleteOrder(onDeleted = onBack)
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteOrder = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    expenseToDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text(stringResource(R.string.expense_delete_title, expense.displayName.localized())) },
            text = { Text(stringResource(R.string.expense_delete_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExpense(expense.id)
                    expenseToDelete = null
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    copyToRemove?.let { copy ->
        AlertDialog(
            onDismissRequest = { copyToRemove = null },
            title = { Text(stringResource(R.string.copy_remove_title, copy.vnTitle)) },
            text = { Text(stringResource(R.string.copy_remove_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeCopyFromOrder(copy.id)
                    copyToRemove = null
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { copyToRemove = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun OrderHeaderCard(detail: OrderDetail) {
    SectionCard {
        val order = detail.order
        order.merchant?.let { LabeledRow(stringResource(R.string.merchant)) { Text(it) } }
        order.orderDate?.let { LabeledRow(stringResource(R.string.date)) { Text(it.toString()) } }
        LabeledRow(stringResource(R.string.default_currency)) { Text(order.currency) }
        order.notes?.let { LabeledRow(stringResource(R.string.notes)) { Text(it) } }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(
                stringResource(R.string.goods_base),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            val goods = detail.breakdown.goodsTotals
            com.vnventory.app.ui.components.MoneyTotalsInline(goods)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(
                stringResource(R.string.fees_all),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            val fees = detail.breakdown.feeTotals
            com.vnventory.app.ui.components.MoneyTotalsInline(fees)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(
                stringResource(R.string.order_actual_spending),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            com.vnventory.app.ui.components.MoneyTotalsInline(
                totals = detail.breakdown.totalsByCurrency,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val known = detail.copies.count { it.priceMinor != null }
        if (known < detail.copies.size) {
            Text(stringResource(R.string.price_coverage, known, detail.copies.size), style = MaterialTheme.typography.bodySmall)
        }
        UnallocatedFees(detail.breakdown.unallocatedTotals)
        detail.breakdown.issues.forEach { Text(it.localized(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun OrderCopyRow(
    copy: OwnedCopy,
    cost: CopyCost?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    ordinal: Int? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        VnCover(
            url = copy.coverUrl,
            contentDescription = copy.vnTitle,
            modifier = Modifier
                .width(48.dp)
                .height(68.dp),
            corner = 8.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = copy.vnTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = copy.displayReleaseName.localized(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ordinal?.let { Text(stringResource(R.string.copy_number, it), style = MaterialTheme.typography.labelSmall) }
            Text(
                text = stringResource(R.string.copy_base_amount, copy.priceMinor?.let { Money.formatWithCode(it, copy.currency) } ?: stringResource(R.string.not_recorded)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val totals = cost?.takeIf { it.feeShares.isNotEmpty() }?.totalsByCurrency.orEmpty()
            if (totals.isNotEmpty()) Text(stringResource(if (copy.priceMinor == null) R.string.recorded_cost else R.string.detail_final_cost), style = MaterialTheme.typography.labelSmall)
            totals.entries.sortedBy { it.key }.forEach { (currency, amount) ->
                Text(
                    text = Money.formatWithCode(amount, currency),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.action_remove), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(expense.displayName.localized(), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (expense.name != expense.category.name) Tag(text = expense.category.label.localized())
                Tag(text = expense.mode.label.localized())
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Money.formatWithCode(expense.amountMinor, expense.currency),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.tap_edit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.expense_delete))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseEditorSheet(
    state: OrderDetailUiState,
    viewModel: OrderDetailViewModel,
) {
    val detail = state.detail ?: return
    val editor = state.editor
    val focus = LocalFocusManager.current

    ModalBottomSheet(
        onDismissRequest = viewModel::closeExpenseEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            ExpenseEditorContent(state, viewModel::onExpenseCategoryChange, viewModel::onExpenseAmountChange,
                viewModel::onExpenseCurrencyChange, viewModel::onExpenseModeChange, viewModel::onManualAmountChange,
                Modifier.weight(1f, fill = false))
            FormSaveBar(
                label = stringResource(R.string.expense_save),
                saving = state.savingExpense,
                onClick = { focus.clearFocus(); viewModel.saveExpense() },
                enabled = state.preview != null && state.editorError == null && !state.savingExpense,
                error = { OperationError(viewModel) },
            )
        }
    }
}

@Composable
internal fun ExpenseEditorContent(
    state: OrderDetailUiState, onCategory: (ExpenseCategory) -> Unit, onAmount: (String) -> Unit,
    onCurrency: (String) -> Unit, onMode: (AllocationMode) -> Unit, onManualAmount: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail ?: return
    val editor = state.editor
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(if (editor.editingId == null) R.string.expense_add else R.string.expense_edit), style = MaterialTheme.typography.titleLarge)
        FormSection(stringResource(R.string.expense_category), R.drawable.ic_ui_batch) {
            if (editor.editingId != null && editor.name != editor.category.name) Text(editor.name, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (ExpenseCategory.fixedCategories + listOfNotNull(editor.category.takeIf { editor.editingId != null && !it.isFixed })).forEach { category ->
                    FilterChip(editor.category == category, { onCategory(category) }, label = { Text(category.label.localized()) })
                }
            }
            MoneyInputField(editor.amountText, editor.currency, stringResource(R.string.amount), onAmount, onCurrency,
                isError = editor.amountText.isNotBlank() && editor.parsedAmount == null)
        }
        FormSection(stringResource(R.string.allocation_mode), R.drawable.ic_ui_preferences) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AllocationMode.entries.forEach { mode ->
                    FilterChip(editor.mode == mode, { onMode(mode) }, label = { Text(mode.label.localized()) },
                        enabled = mode != AllocationMode.BY_PRICE || (detail.copies.all { it.priceMinor != null } && detail.copies.map { it.currency }.distinct().size <= 1))
                }
            }
            if (editor.mode == AllocationMode.MANUAL) ManualAllocationEditor(detail, editor, onManualAmount)
            else if (detail.copies.map { it.currency }.distinct().size > 1) Text(stringResource(R.string.allocation_mixed_hint), style = MaterialTheme.typography.bodySmall)
            state.editorError?.let { Text(it.localized(), color = MaterialTheme.colorScheme.error) }
        }
        state.preview?.let { preview ->
            FormSection(stringResource(R.string.allocation_preview), R.drawable.ic_ui_list) { AllocationPreview(detail, preview) }
        }
    }
}

@Composable
private fun AllocationPreview(detail: OrderDetail, preview: OrderCostBreakdown) {
    Column {
        val known = detail.copies.count { it.priceMinor != null }
        if (known < detail.copies.size) Text(stringResource(R.string.price_coverage, known, detail.copies.size), style = MaterialTheme.typography.bodySmall)
        detail.copies.forEach { copy ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = detail.copies.copyOrdinal(copy)?.let { stringResource(R.string.text_pair, copy.vnTitle, stringResource(R.string.copy_number, it)) } ?: copy.vnTitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                com.vnventory.app.ui.components.MoneyTotalsInline(
                    preview.copyCosts.first { it.copyId == copy.id }.totalsByCurrency,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        LabeledRow(stringResource(R.string.order_total_spending)) { com.vnventory.app.ui.components.MoneyTotalsInline(preview.totalsByCurrency) }
        UnallocatedFees(preview.unallocatedTotals)
    }
}

@Composable
internal fun UnallocatedFees(totals: Map<String, Long>) {
    if (totals.isNotEmpty()) LabeledRow(stringResource(R.string.unallocated_fees)) {
        com.vnventory.app.ui.components.MoneyTotalsInline(totals, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ManualAllocationEditor(
    detail: OrderDetail,
    editor: ExpenseEditorState,
    onAmountChange: (Long, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (detail.copies.isEmpty()) {
            Text(
                text = stringResource(R.string.allocation_no_copies),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        detail.copies.forEach { copy ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column {
                    Text(
                        text = detail.copies.copyOrdinal(copy)?.let { stringResource(R.string.text_pair, copy.vnTitle, stringResource(R.string.copy_number, it)) } ?: copy.vnTitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = copy.displayReleaseName.localized(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedTextField(
                    value = editor.manualInputs[copy.id].orEmpty(),
                    onValueChange = { onAmountChange(copy.id, it) },
                    isError = !editor.manualInputs[copy.id].isNullOrBlank() && Money.parse(editor.manualInputs[copy.id]!!, editor.currency) == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(stringResource(R.string.amount)) }, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().testTag("manual-allocation-${copy.id}"),
                )
            }
        }

        val allocated = editor.parsedManual?.values?.let { runCatching { Money.sum(it) }.getOrNull() }
        if (allocated == null) {
            Text(stringResource(R.string.allocation_input_invalid), color = MaterialTheme.colorScheme.error)
            return@Column
        }
        val total = editor.parsedAmount ?: 0L
        val diff = total - allocated
        Text(
            text = stringResource(R.string.allocation_totals, Money.format(allocated, editor.currency), Money.format(total, editor.currency), Money.format(diff, editor.currency)),
            style = MaterialTheme.typography.bodySmall,
            color = if (diff == 0L) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            text = stringResource(R.string.allocation_blank_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
