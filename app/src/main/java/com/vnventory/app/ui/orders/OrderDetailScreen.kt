package com.vnventory.app.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.vnventory.app.ui.components.CurrencySelector
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.LabeledRow
import com.vnventory.app.ui.components.LoadingState
import com.vnventory.app.ui.components.SectionCard
import com.vnventory.app.ui.components.Tag
import com.vnventory.app.ui.components.VnCover

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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.detail?.order?.title ?: "订单",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.detail != null) {
                        IconButton(onClick = { confirmDeleteOrder = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除订单")
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
                title = "订单不存在或已被删除",
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { OrderHeaderCard(detail) }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "包含的游戏（${detail.copies.size} 盒）",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onAddCopies(detail.order.id) }) { Text("添加游戏") }
                    }
                }

                if (detail.copies.isEmpty()) {
                    item {
                        Text(
                            text = "还没有加入游戏。点“添加游戏”从 VNDB 搜索，或把已有收藏加入本订单。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(detail.copies, key = { it.id }) { copy ->
                    OrderCopyRow(
                        copy = copy,
                        cost = detail.costFor(copy.id),
                        onClick = { onCopyClick(copy.id) },
                        onRemove = { copyToRemove = copy },
                    )
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "费用（${detail.expenses.size}）",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::openNewExpense) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("添加费用")
                        }
                    }
                }

                if (detail.expenses.isEmpty()) {
                    item {
                        Text(
                            text = "举例：日本国内运费、国际运费、支付手续费、税费。加好后会自动分摊到每一盒。",
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
            title = { Text("删除这个订单？") },
            text = {
                Text(
                    "订单里的费用会一起删除；${state.detail?.copies?.size ?: 0} 盒收藏会保留，" +
                        "只解除批次归属（之后可重新加入其它订单）。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteOrder = false
                    viewModel.deleteOrder(onDeleted = onBack)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteOrder = false }) { Text("取消") }
            },
        )
    }

    expenseToDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("删除费用“${expense.name}”？") },
            text = { Text("删除后所有盒子的成本会立即重新计算。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExpense(expense.id)
                    expenseToDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) { Text("取消") }
            },
        )
    }

    copyToRemove?.let { copy ->
        AlertDialog(
            onDismissRequest = { copyToRemove = null },
            title = { Text("把“${copy.vnTitle}”移出本订单？") },
            text = { Text("收藏本身会保留（变成独立收藏），只是不再分摊这个批次的费用。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeCopyFromOrder(copy.id)
                    copyToRemove = null
                }) { Text("移出") }
            },
            dismissButton = {
                TextButton(onClick = { copyToRemove = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun OrderHeaderCard(detail: OrderDetail) {
    SectionCard {
        val order = detail.order
        order.merchant?.let { LabeledRow("商家") { Text(it) } }
        order.orderDate?.let { LabeledRow("日期") { Text(it.toString()) } }
        LabeledRow("默认币种") { Text(order.currency) }
        order.notes?.let { LabeledRow("备注") { Text(it) } }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(
                "商品本体",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            val goods = detail.copies
                .groupBy { it.currency }
                .mapValues { entry -> entry.value.sumOf { it.priceMinor } }
            com.vnventory.app.ui.components.MoneyTotalsInline(goods)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(
                "全部费用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            val fees = detail.expenses
                .groupBy { it.currency }
                .mapValues { entry -> entry.value.sumOf { it.amountMinor } }
            com.vnventory.app.ui.components.MoneyTotalsInline(fees)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(
                "合计",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            com.vnventory.app.ui.components.MoneyTotalsInline(
                totals = detail.breakdown.totalsByCurrency,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun OrderCopyRow(
    copy: OwnedCopy,
    cost: CopyCost?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
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
                text = copy.displayReleaseName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "本体 " + Money.formatWithCode(copy.priceMinor, copy.currency),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val totals = cost?.totalsByCurrency ?: mapOf(copy.currency to copy.priceMinor)
            totals.entries.sortedBy { it.key }.forEach { (currency, amount) ->
                Text(
                    text = Money.format(amount, currency),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = onRemove) {
                Text("移出", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(expense.name, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(text = expense.category.label)
                Tag(text = expense.mode.label)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Money.formatWithCode(expense.amountMinor, expense.currency),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "点击编辑",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "删除费用")
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

    ModalBottomSheet(
        onDismissRequest = viewModel::closeExpenseEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (editor.editingId == null) "添加费用" else "编辑费用",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = editor.name,
                onValueChange = viewModel::onExpenseNameChange,
                label = { Text("费用名称，如：国际运费") },
                singleLine = true,
                isError = editor.name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("分类", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExpenseCategory.entries.forEach { category ->
                        FilterChip(
                            selected = editor.category == category,
                            onClick = { viewModel.onExpenseCategoryChange(category) },
                            label = { Text(category.label) },
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = editor.amountText,
                    onValueChange = viewModel::onExpenseAmountChange,
                    label = { Text("金额") },
                    isError = editor.amountText.isNotBlank() && editor.parsedAmount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                CurrencySelector(
                    selected = editor.currency,
                    onSelect = viewModel::onExpenseCurrencyChange,
                )
            }

            Column {
                Text("分摊方式", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AllocationMode.entries.forEach { mode ->
                        FilterChip(
                            selected = editor.mode == mode,
                            onClick = { viewModel.onExpenseModeChange(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }

            when (editor.mode) {
                AllocationMode.EQUAL, AllocationMode.BY_PRICE -> {
                    AllocationPreview(detail = detail, editor = editor)
                }

                AllocationMode.MANUAL -> {
                    ManualAllocationEditor(
                        detail = detail,
                        editor = editor,
                        onAmountChange = viewModel::onManualAmountChange,
                    )
                }
            }

            Button(
                onClick = viewModel::saveExpense,
                enabled = editor.canSave && !state.savingExpense,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.savingExpense) "保存中…" else "保存费用")
            }
        }
    }
}

@Composable
private fun AllocationPreview(detail: OrderDetail, editor: ExpenseEditorState) {
    val amount = editor.parsedAmount ?: return
    if (detail.copies.isEmpty()) {
        Text(
            text = "订单里还没有游戏，无法预览分摊",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val preview = CostEngine.allocateExpense(
        expense = CostExpenseInput(
            expenseId = 0,
            name = editor.name,
            category = editor.category,
            amountMinor = amount,
            currency = editor.currency,
            mode = editor.mode,
        ),
        copies = detail.copies.map { CostCopyInput(it.id, it.priceMinor, it.currency) },
    )

    Column {
        Text("预览（每盒分摊）", style = MaterialTheme.typography.labelMedium)
        detail.copies.forEach { copy ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    text = copy.vnTitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Money.formatWithCode(preview[copy.id] ?: 0L, editor.currency),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
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
                text = "订单里还没有游戏，先添加游戏再手动分配",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        detail.copies.forEach { copy ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = copy.vnTitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = copy.displayReleaseName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = editor.manualInputs[copy.id].orEmpty(),
                    onValueChange = { onAmountChange(copy.id, it) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(120.dp),
                )
            }
        }

        val allocated = editor.manualInputs.values
            .mapNotNull { Money.parse(it, editor.currency) }
            .sum()
        val total = editor.parsedAmount ?: 0L
        val diff = total - allocated
        Text(
            text = "已分配 ${Money.format(allocated, editor.currency)} / " +
                "总额 ${Money.format(total, editor.currency)}（差额 ${Money.format(diff, editor.currency)}）",
            style = MaterialTheme.typography.bodySmall,
            color = if (diff == 0L) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            text = "提示：允许差额（例如一部分费用不进成本），但通常应分完。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
