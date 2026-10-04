package com.vnventory.app.ui.edit

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.Money
import com.vnventory.app.ui.components.CurrencySelector
import com.vnventory.app.ui.components.DateField
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.ErrorState
import com.vnventory.app.ui.components.LabeledRow
import com.vnventory.app.ui.components.LoadingState
import com.vnventory.app.ui.components.OrderSelector
import com.vnventory.app.ui.components.VnCover
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.ui.components.SaveButton
import androidx.compose.ui.res.stringResource
import com.vnventory.app.R
import com.vnventory.app.ui.components.ShopChannelField
import com.vnventory.app.ui.text.localized

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CopyEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_copy)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            Column {
            OperationError(viewModel)
            if (state.copy != null) {
                Surface(tonalElevation = 3.dp) {
                    SaveButton(
                        label = stringResource(R.string.edit_copy_save),
                        saving = state.saving,
                        onClick = { viewModel.save(onSaved) },
                        enabled = state.form.canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(padding))

            state.notFound -> EmptyState(
                title = stringResource(R.string.copy_not_found),
                modifier = Modifier.padding(padding),
            )

            else -> {
                val copy = state.copy ?: return@Scaffold
                val form = state.form

                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row {
                        VnCover(
                            url = copy.coverUrl,
                            contentDescription = copy.vnTitle,
                            modifier = Modifier
                                .width(64.dp)
                                .height(88.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(copy.vnTitle, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = copy.displayReleaseName.localized(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            if (copy.isManualRelease) {
                                OutlinedButton(onClick = { viewModel.openBindSheet() }) {
                                    Text(stringResource(R.string.release_bind))
                                }
                            }
                        }
                    }

                    if (copy.isManualRelease) {
                        OutlinedTextField(
                            value = form.releaseTitle,
                            onValueChange = viewModel::onReleaseTitleChange,
                            label = { Text(stringResource(R.string.release_manual_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = form.priceText,
                            onValueChange = viewModel::onPriceChange,
                            label = { Text(stringResource(R.string.purchase_price)) },
                            isError = !form.priceValid,
                            supportingText = {
                                val parsed = form.parsedPrice
                                Text(
                                    when {
                                        form.priceText.isBlank() -> stringResource(R.string.amount_blank_zero)
                                        parsed != null -> stringResource(R.string.amount_equivalent, Money.format(parsed, form.currency))
                                        else -> stringResource(R.string.amount_invalid)
                                    }
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        CurrencySelector(
                            selected = form.currency,
                            onSelect = viewModel::onCurrencyChange,
                        )
                    }

                    Column {
                        Text(stringResource(R.string.condition), style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CopyCondition.entries.forEach { condition ->
                                FilterChip(
                                    selected = form.condition == condition,
                                    onClick = { viewModel.onConditionChange(condition) },
                                    label = { Text(condition.label.localized()) },
                                )
                            }
                        }
                    }

                    if (form.condition == CopyCondition.CUSTOM) {
                        OutlinedTextField(
                            value = form.conditionNote,
                            onValueChange = viewModel::onConditionNoteChange,
                            label = { Text(stringResource(R.string.condition_note)) },
                            isError = !form.conditionValid,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    LabeledRow(stringResource(R.string.purchase_date)) {
                        DateField(
                            date = form.purchaseDate,
                            onDateChange = viewModel::onPurchaseDateChange,
                        )
                    }

                    ShopChannelField(
                        value = form.shop,
                        onValueChange = viewModel::onShopChange,
                        options = state.shopChannels,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    LabeledRow(stringResource(R.string.owned_order)) {
                        OrderSelector(
                            orders = state.orders,
                            selectedId = form.orderId,
                            onSelect = viewModel::onOrderChange,
                        )
                    }

                    OutlinedTextField(
                        value = form.notes,
                        onValueChange = viewModel::onNotesChange,
                        label = { Text(stringResource(R.string.notes_optional)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // 绑定 VNDB Release 面板
    if (state.bindSheet.open) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeBindSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OperationError(viewModel)
                Text(
                    text = stringResource(R.string.release_bind_select),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                val bindError = state.bindSheet.error
                when {
                    state.bindSheet.loading && state.bindSheet.releases.isEmpty() ->
                        LoadingState(modifier = Modifier.height(200.dp))

                    bindError != null && state.bindSheet.releases.isEmpty() ->
                        ErrorState(
                            message = bindError.localized(),
                            modifier = Modifier.height(200.dp),
                        )

                    state.bindSheet.releases.isEmpty() -> EmptyState(
                        title = stringResource(R.string.release_bind_empty),
                        subtitle = stringResource(R.string.release_bind_empty_hint),
                        modifier = Modifier.height(200.dp),
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(bottom = 32.dp),
                        modifier = Modifier.heightIn(max = 480.dp),
                    ) {
                        items(state.bindSheet.releases, key = { it.id }) { release ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.bindTo(release) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                VnCover(
                                    url = release.displayImage(),
                                    contentDescription = release.title,
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(68.dp),
                                    corner = 8.dp,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = release.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildString {
                                            append(release.released ?: stringResource(R.string.release_date_unknown))
                                            if (release.platforms.isNotEmpty()) {
                                                append(stringResource(R.string.separator_dot))
                                                append(release.platforms.joinToString("/"))
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
