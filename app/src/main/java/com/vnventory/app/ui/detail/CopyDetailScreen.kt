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
import androidx.compose.ui.res.stringResource
import com.vnventory.app.R
import com.vnventory.app.ui.text.localized

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
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    state.copy?.let { copy ->
                        IconButton(onClick = { onEdit(copy.id) }) { Icon(Icons.Default.Edit, stringResource(R.string.detail_edit)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.detail_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
            )
        },
    ) { padding -> CopyDetailContent(state, onOrderClick, Modifier.padding(padding)) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_hint, state.copy?.id ?: 0L)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) { Text(stringResource(R.string.detail_delete_record), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.detail_keep)) } },
        )
    }
}

@Composable
fun CopyDetailContent(state: CopyDetailUiState, onOrderClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    when {
        state.loading -> LoadingState(modifier, stringResource(R.string.detail_loading))
        state.notFound -> EmptyState(stringResource(R.string.detail_missing_title), modifier, stringResource(R.string.detail_missing_hint))
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
                        label = stringResource(R.string.detail_final_cost),
                        supporting = stringResource(if (copy.orderId == null) R.string.detail_standalone_hint else R.string.detail_order_cost_hint),
                    )
                }
                item(key = "costBreakdown") {
                    SectionCard(title = stringResource(R.string.detail_cost_breakdown)) {
                        LabeledRow(stringResource(R.string.base_price)) { Text(Money.formatWithCode(copy.priceMinor, copy.currency)) }
                        state.cost?.feeShares?.forEach { share ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(share.label.localized(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Money.formatWithCode(share.amountMinor, share.currency), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        state.orderDetail?.breakdown?.issues?.forEach { Text(it.localized(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        state.orderDetail?.order?.let { order ->
                            TextButton(onClick = { onOrderClick(order.id) }) { Text(stringResource(R.string.detail_view_order, order.title)) }
                        }
                        if (copy.orderId == null) Text(stringResource(R.string.detail_join_order_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(key = "purchase") {
                    SectionCard(title = stringResource(R.string.detail_purchase_record)) {
                        LabeledRow(stringResource(R.string.condition)) {
                            val label = copy.condition.label.localized()
                            Text(copy.conditionNote?.let { stringResource(R.string.text_pair, label, it) } ?: label)
                        }
                        LabeledRow(stringResource(R.string.purchase_date)) { Text(copy.purchaseDate?.toString() ?: stringResource(R.string.not_recorded)) }
                        LabeledRow(stringResource(R.string.shop_channel)) { Text(copy.shop ?: stringResource(R.string.not_recorded)) }
                        LabeledRow(stringResource(R.string.notes)) { Text(copy.notes ?: stringResource(R.string.no_notes)) }
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
        Text(state.release?.title ?: copy.displayReleaseName.localized(), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(copy.condition.label.localized(), emphasized = true)
            Tag(stringResource(R.string.copy_number, copy.id))
            if (copy.isManualRelease) Tag(stringResource(R.string.message_manual_release))
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
    SectionCard(title = stringResource(R.string.detail_release_metadata)) {
        release?.let {
            LabeledRow(stringResource(R.string.release_id)) { Text(it.id) }
            LabeledRow(stringResource(R.string.release_date)) { Text(it.released ?: stringResource(R.string.unknown)) }
            if (it.languages.isNotEmpty()) LabeledRow(stringResource(R.string.language)) { Text(it.languages.joinToString(stringResource(R.string.separator_slash))) }
            if (it.publishers.isNotEmpty()) LabeledRow(stringResource(R.string.publisher)) { Text(it.publishers.joinToString(stringResource(R.string.separator_names))) }
            it.jan?.let { code -> LabeledRow(stringResource(R.string.barcode_label)) { Text(code) } }
        }
        vn?.let {
            LabeledRow(stringResource(R.string.vn_id)) { Text(it.id) }
            if (release == null) {
                LabeledRow(stringResource(R.string.vn_release_date)) { Text(it.released ?: stringResource(R.string.unknown)) }
                it.altTitle?.let { title -> LabeledRow(stringResource(R.string.vn_original_title)) { Text(title) } }
            }
        }
        description?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, Modifier.animateContentSize(tween(ShelfMotion.Standard)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) Int.MAX_VALUE else 4)
            TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.description_collapse else R.string.description_expand)) }
        }
    }
}
