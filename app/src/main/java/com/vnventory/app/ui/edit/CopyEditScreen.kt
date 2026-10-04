package com.vnventory.app.ui.edit

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.Money
import com.vnventory.app.ui.components.DateField
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.ErrorState
import com.vnventory.app.ui.components.LoadingState
import com.vnventory.app.ui.components.OrderSelector
import com.vnventory.app.ui.components.VnCover
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.ui.components.FormSaveBar
import com.vnventory.app.ui.components.FormSection
import com.vnventory.app.ui.components.MoneyInputField
import com.vnventory.app.ui.components.ConditionPicker
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
    val focus = LocalFocusManager.current

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
            if (state.copy != null) {
                FormSaveBar(stringResource(R.string.edit_copy_save), state.saving, state.form.canSave,
                    { focus.clearFocus(); viewModel.save(onSaved) }, error = { OperationError(viewModel) })
            } else {
                OperationError(viewModel)
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(padding))

            state.notFound -> EmptyState(
                title = stringResource(R.string.copy_not_found),
                modifier = Modifier.padding(padding),
            )

            else -> CopyEditContent(state, viewModel::onFormChange, viewModel::openBindSheet, Modifier.padding(padding))
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

@Composable
internal fun CopyEditContent(state: CopyEditUiState, onFormChange: (EditFormState) -> Unit, onBind: () -> Unit, modifier: Modifier = Modifier) {
    val copy = state.copy ?: return
    val form = state.form
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormSection(stringResource(R.string.purchase_selected_release), R.drawable.ic_ui_shelf) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                VnCover(copy.coverUrl, copy.vnTitle, Modifier.width(56.dp).height(80.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(copy.vnTitle, style = MaterialTheme.typography.titleMedium)
                    Text(copy.displayReleaseName.localized(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (copy.isManualRelease) {
                OutlinedTextField(form.releaseTitle, { onFormChange(form.copy(releaseTitle = it)) },
                    label = { Text(stringResource(R.string.release_manual_name)) }, singleLine = true,
                    shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = onBind, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                    Text(stringResource(R.string.release_bind))
                }
            }
        }
        FormSection(stringResource(R.string.purchase_price), R.drawable.ic_ui_batch) {
            MoneyInputField(form.priceText, form.currency, stringResource(R.string.purchase_unit_price),
                { onFormChange(form.copy(priceText = it)) }, { onFormChange(form.copy(currency = it)) },
                isError = !form.priceValid, supportingText = when {
                    form.priceText.isBlank() -> stringResource(R.string.amount_blank_unknown)
                    form.parsedPrice != null -> stringResource(R.string.amount_equivalent, Money.format(form.parsedPrice!!, form.currency))
                    else -> stringResource(R.string.amount_invalid)
                })
        }
        FormSection(stringResource(R.string.condition), R.drawable.ic_ui_preferences) {
            ConditionPicker(form.condition, form.conditionNote,
                { onFormChange(form.copy(condition = it)) }, { onFormChange(form.copy(conditionNote = it)) })
        }
        FormSection(stringResource(R.string.purchase_records), R.drawable.ic_ui_shop, hint = stringResource(R.string.purchase_records_hint)) {
            Text(stringResource(R.string.purchase_date), style = MaterialTheme.typography.labelMedium)
            DateField(form.purchaseDate, { onFormChange(form.copy(purchaseDate = it)) }, Modifier.fillMaxWidth(), stringResource(R.string.purchase_date_optional))
            ShopChannelField(form.shop, { onFormChange(form.copy(shop = it)) }, state.shopChannels, Modifier.fillMaxWidth())
            Text(stringResource(R.string.purchase_order), style = MaterialTheme.typography.labelMedium)
            OrderSelector(state.orders, form.orderId, { onFormChange(form.copy(orderId = it)) }, Modifier.fillMaxWidth())
        }
        FormSection(stringResource(R.string.notes), R.drawable.ic_ui_info) {
            OutlinedTextField(form.notes, { onFormChange(form.copy(notes = it)) },
                placeholder = { Text(stringResource(R.string.purchase_notes_placeholder)) }, minLines = 3,
                shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
        }
    }
}
