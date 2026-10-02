package com.vnventory.app.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.Money
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.LabeledRow
import com.vnventory.app.ui.components.LoadingState
import com.vnventory.app.ui.components.SectionCard
import com.vnventory.app.ui.components.Tag
import com.vnventory.app.ui.components.VnCover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOrderClick: (Long) -> Unit,
    viewModel: CopyDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.copy?.vnTitle ?: "收藏详情",
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
                    val copy = state.copy
                    if (copy != null) {
                        IconButton(onClick = { onEdit(copy.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(padding))

            state.notFound -> EmptyState(
                title = "收藏不存在或已被删除",
                modifier = Modifier.padding(padding),
            )

            else -> {
                val copy = state.copy ?: return@Scaffold
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row {
                            VnCover(
                                url = state.coverUrl,
                                contentDescription = copy.vnTitle,
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(168.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(copy.vnTitle, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = state.release?.title ?: copy.displayReleaseName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Tag(text = copy.condition.label, emphasized = true)
                                    if (copy.isManualRelease) Tag(text = "手动版本")
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = copy.releaseId ?: copy.vnId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item {
                        SectionCard(title = "购买信息") {
                            LabeledRow("购入价格") {
                                Text(Money.formatWithCode(copy.priceMinor, copy.currency))
                            }
                            LabeledRow("品相") {
                                Text(
                                    buildString {
                                        append(copy.condition.label)
                                        copy.conditionNote?.let { append("：$it") }
                                    }
                                )
                            }
                            LabeledRow("购买日期") { Text(copy.purchaseDate?.toString() ?: "—") }
                            LabeledRow("店铺") { Text(copy.shop ?: "—") }
                            LabeledRow("所属订单") {
                                val order = state.orderDetail?.order
                                if (order == null) {
                                    Text("未加入订单")
                                } else {
                                    Text(
                                        text = order.title,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickableText { onOrderClick(order.id) },
                                    )
                                }
                            }
                            LabeledRow("备注") { Text(copy.notes ?: "—") }
                        }
                    }

                    item { CostCard(state, onOrderClick) }

                    item {
                        val release = state.release
                        val vn = state.vn
                        if (release != null || vn != null) {
                            SectionCard(title = "VNDB 信息") {
                                release?.let { r ->
                                    LabeledRow("发行日期") { Text(r.released ?: "—") }
                                    if (r.platforms.isNotEmpty()) {
                                        LabeledRow("平台") { Text(r.platforms.joinToString("、")) }
                                    }
                                    if (r.languages.isNotEmpty()) {
                                        LabeledRow("语言") { Text(r.languages.joinToString("、")) }
                                    }
                                    if (r.publishers.isNotEmpty()) {
                                        LabeledRow("发行商") { Text(r.publishers.joinToString("、")) }
                                    }
                                    r.jan?.let { jan -> LabeledRow("JAN/EAN") { Text(jan) } }
                                }
                                vn?.let { info ->
                                    if (release == null) {
                                        LabeledRow("发售") { Text(info.released ?: "—") }
                                        info.altTitle?.let { alt -> LabeledRow("原题") { Text(alt) } }
                                    }
                                    info.description?.let { desc ->
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = desc.replace(Regex("\\[/?url[^\\]]*\\]"), "").trim(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 8,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这盒收藏？") },
            text = { Text("将删除这一盒的记录（含价格与备注）。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDeleted = onBack)
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CostCard(state: CopyDetailUiState, onOrderClick: (Long) -> Unit) {
    val copy = state.copy ?: return
    val cost = state.cost

    SectionCard(title = "成本") {
        LabeledRow("本体价格") {
            Text(Money.formatWithCode(copy.priceMinor, copy.currency))
        }

        if (cost == null) {
            Text(
                text = "未加入购买批次：最终实际成本 = 购入价格。把它加入订单即可分摊运费/手续费。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            if (cost.feeShares.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "分摊费用",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                cost.feeShares.forEach { share ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = share.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = Money.formatWithCode(share.amountMinor, share.currency),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "最终实际成本",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    cost.totalsByCurrency.entries.sortedBy { it.key }.forEach { (currency, amount) ->
                        Text(
                            text = Money.formatWithCode(amount, currency),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            val order = state.orderDetail?.order
            if (order != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "来自订单：${order.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickableText { onOrderClick(order.id) },
                )
            }
        }
    }
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
