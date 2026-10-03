package com.vnventory.app.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.Money
import com.vnventory.app.ui.components.CostHighlight
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.LabeledRow
import com.vnventory.app.ui.components.LoadingState
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.ui.components.SectionCard
import com.vnventory.app.ui.components.Tag
import com.vnventory.app.ui.components.VnCover
import com.vnventory.app.ui.theme.ShelfMotion

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
        bottomBar = { OperationError(viewModel) },
        topBar = {
            TopAppBar(
                title = { Text("收藏档案") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    state.copy?.let { copy ->
                        IconButton(onClick = { onEdit(copy.id) }) { Icon(Icons.Default.Edit, "编辑这盒收藏") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "删除这盒收藏", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
            )
        },
    ) { padding -> CopyDetailContent(state, onOrderClick, Modifier.padding(padding)) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这盒收藏？") },
            text = { Text("只删除盒 #${state.copy?.id} 的记录，包括价格与备注。相同版本的其他盒子不受影响。此操作不可撤销。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) { Text("删除记录", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("保留收藏") } },
        )
    }
}

@Composable
fun CopyDetailContent(state: CopyDetailUiState, onOrderClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    when {
        state.loading -> LoadingState(modifier, "打开收藏档案…")
        state.notFound -> EmptyState("这盒收藏已不在书架上", modifier, "记录可能已被删除。")
        else -> {
            val copy = state.copy ?: return
            LazyColumn(
                modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(key = "poster") { DetailPoster(state) }
                item(key = "cost") {
                    CostHighlight(
                        totals = state.cost?.totalsByCurrency ?: mapOf(copy.currency to copy.priceMinor),
                        label = "这盒的最终实际成本",
                        supporting = if (copy.orderId == null) "当前为独立收藏，成本等于购入价格。" else "购入价格与批次费用分摊实时计算，不重复存储结果。",
                    )
                }
                item(key = "costBreakdown") {
                    SectionCard(title = "成本明细") {
                        LabeledRow("本体价格") { Text(Money.formatWithCode(copy.priceMinor, copy.currency)) }
                        state.cost?.feeShares?.forEach { share ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(share.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Money.formatWithCode(share.amountMinor, share.currency), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        state.orderDetail?.breakdown?.issues?.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        state.orderDetail?.order?.let { order ->
                            TextButton(onClick = { onOrderClick(order.id) }) { Text("查看购买批次：${order.title}") }
                        }
                        if (copy.orderId == null) Text("编辑收藏并加入购买批次，就可以分摊运费和手续费。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(key = "purchase") {
                    SectionCard(title = "这一盒的购买记录") {
                        LabeledRow("品相") { Text(copy.condition.label + (copy.conditionNote?.let { " · $it" } ?: "")) }
                        LabeledRow("购买日期") { Text(copy.purchaseDate?.toString() ?: "未记录") }
                        LabeledRow("店铺 / 渠道") { Text(copy.shop ?: "未记录") }
                        LabeledRow("备注") { Text(copy.notes ?: "还没有备注") }
                    }
                }
                item(key = "metadata") { VndbInfoCard(state) }
            }
        }
    }
}

@Composable
private fun DetailPoster(state: CopyDetailUiState) {
    val copy = state.copy ?: return
    val colors = MaterialTheme.colorScheme
    SectionCard {
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha = .65f), colors.surfaceContainerLow)))
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            VnCover(
                state.coverUrl, copy.vnTitle,
                Modifier.width(152.dp).height(218.dp).shadow(14.dp, RoundedCornerShape(16.dp)),
                corner = 16.dp, contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(copy.vnTitle, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(state.release?.title ?: copy.displayReleaseName, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(copy.condition.label, emphasized = true)
            Tag("盒 #${copy.id}")
            if (copy.isManualRelease) Tag("手动版本")
            state.release?.platforms?.forEach { Tag(it) }
        }
    }
}

@Composable
private fun VndbInfoCard(state: CopyDetailUiState) {
    val release = state.release
    val vn = state.vn
    if (release == null && vn == null) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val description = remember(vn?.description) { vn?.description?.replace(Regex("\\[/?url[^\\]]*\\]"), "")?.trim() }
    SectionCard(title = "版本资料 · VNDB") {
        release?.let {
            LabeledRow("版本 ID") { Text(it.id) }
            LabeledRow("发行日期") { Text(it.released ?: "未知") }
            if (it.languages.isNotEmpty()) LabeledRow("语言") { Text(it.languages.joinToString(" / ")) }
            if (it.publishers.isNotEmpty()) LabeledRow("发行商") { Text(it.publishers.joinToString("、")) }
            it.jan?.let { code -> LabeledRow("JAN / EAN") { Text(code) } }
        }
        vn?.let {
            LabeledRow("作品 ID") { Text(it.id) }
            if (release == null) {
                LabeledRow("发售日期") { Text(it.released ?: "未知") }
                it.altTitle?.let { title -> LabeledRow("原题") { Text(title) } }
            }
        }
        description?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, Modifier.animateContentSize(tween(ShelfMotion.Standard)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) Int.MAX_VALUE else 4)
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起作品简介" else "展开作品简介") }
        }
    }
}
