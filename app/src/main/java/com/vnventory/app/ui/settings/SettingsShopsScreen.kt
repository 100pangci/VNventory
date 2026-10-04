package com.vnventory.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.R
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.OperationError

@Composable
fun SettingsShopsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val shops by viewModel.shopChannels.collectAsStateWithLifecycle()
    val editor by viewModel.shopEditor.collectAsStateWithLifecycle()
    val busy by viewModel.shopsBusy.collectAsStateWithLifecycle()
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsDetailScaffold(stringResource(R.string.shops_settings), onBack, busy) { padding ->
        SettingsShopsContent(
            shops, { viewModel.openShopEditor() }, viewModel::openShopEditor,
            { viewModel.clearError(); deleting = it }, modifier = Modifier.padding(padding), busy = busy,
            error = { if (!editor.open && deleting == null) OperationError(viewModel) },
        )
    }
    if (editor.open) ShopEditorDialog(
        editor, busy, viewModel::onShopNameChange, viewModel::saveShop, viewModel::dismissShopEditor,
        error = { OperationError(viewModel) },
    )
    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { if (!busy) { viewModel.clearError(); deleting = null } },
            title = { Text(stringResource(R.string.shops_delete_title, name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.shops_delete_hint))
                    OperationError(viewModel)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteShop(name) { deleting = null } }, enabled = !busy) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearError(); deleting = null }, enabled = !busy) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
internal fun SettingsShopsContent(
    shops: List<String>,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    error: @Composable () -> Unit = {},
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.shops_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onAdd, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.shops_add))
                }
                Text(pluralStringResource(R.plurals.shops_count, shops.size, shops.size), style = MaterialTheme.typography.labelLarge)
                error()
            }
        }
        if (shops.isEmpty()) item {
            EmptyState(stringResource(R.string.shops_empty), subtitle = stringResource(R.string.shops_empty_hint))
        }
        items(shops, key = { it }) { name ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onEdit(name) }, enabled = !busy) { Text(stringResource(R.string.action_edit)) }
                        TextButton(onClick = { onDelete(name) }, enabled = !busy) {
                            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShopEditorDialog(
    state: ShopEditorState,
    busy: Boolean,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    error: @Composable () -> Unit = {},
) {
    // Give the editable form explicit bounds instead of AlertDialog's intrinsic text-slot sizing.
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(24.dp).widthIn(max = 560.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(if (state.original == null) R.string.shops_add else R.string.shops_edit),
                    style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    enabled = !busy,
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!busy && state.name.isNotBlank()) onSave() }),
                    label = { Text(stringResource(R.string.shops_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.shops_name_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                error()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = onSave, enabled = !busy && state.name.isNotBlank()) {
                        Text(stringResource(if (busy) R.string.saving else R.string.action_save))
                    }
                }
            }
        }
    }
}
